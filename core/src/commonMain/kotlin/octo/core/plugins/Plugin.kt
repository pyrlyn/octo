package octo.core.plugins

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import octo.core.status.AgentStatus
import octo.core.task.TaskView
import wasm.WasmException
import wasm.WasmInstance
import wasm.WasmRuntime
import wasm.WasmValue
import wasm.readPacked

/** A task as an action sees it: names only, nothing that reaches the agent or the files. */
@Serializable
data class PluginTask(
    val title: String,
    val repo: String,
    val branch: String,
    val baseRef: String,
    val agent: String,
    val status: AgentStatus,
) {
    companion object {
        fun of(view: TaskView) =
            PluginTask(
                title = view.task.title,
                repo = view.task.repo,
                branch = view.task.branch,
                baseRef = view.task.baseRef,
                agent = view.task.agent.value,
                status = view.status,
            )
    }
}

/** What an action asks for: a [prompt] to send to the task's agent, a [message] to show, or both. */
data class PluginResult(
    val prompt: String?,
    val message: String?,
)

class PluginException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * A loaded plugin. Its module imports nothing, so it can reach no file, process or network; it
 * only turns the JSON it is given into JSON. Each call runs on a fuel budget, so a plugin that
 * never returns is stopped, and calls on one instance take turns because the guest is not reentrant.
 */
class Plugin private constructor(
    val manifest: PluginManifest,
    /** The file it came from, for messages. */
    val file: String,
    private val instance: WasmInstance,
    private val fuel: Long,
) {
    private val lock = Mutex()

    suspend fun run(
        action: String,
        task: PluginTask,
    ): PluginResult {
        require(manifest.actions.any { it.id == action }) { "Plugin '${manifest.id}' has no action '$action'" }
        val input = json.encodeToString(RunInput.serializer(), RunInput(action, task)).encodeToByteArray()
        val output = lock.withLock { withContext(Dispatchers.Default) { guarded { call(input) } } }
        val result =
            try {
                json.decodeFromString(RunOutput.serializer(), output.decodeToString())
            } catch (e: SerializationException) {
                throw PluginException("Plugin '${manifest.id}' answered with invalid JSON: ${e.message}", e)
            }
        return result.checked()
    }

    private fun call(input: ByteArray): ByteArray {
        instance.setFuel(fuel)
        val ptr = copyIn(input)
        val packed = instance.i64(RUN, listOf(WasmValue.I32(ptr), WasmValue.I32(input.size)))
        if (packed < 0) throw WasmException("the plugin refused the action")
        return readPacked(instance.memory, packed, MAX_OUTPUT)
    }

    /** Copies [input] into memory the guest allocated for it and returns where it went. */
    private fun copyIn(input: ByteArray): Int {
        val memory = instance.memory ?: throw WasmException("guest memory export missing")
        val ptr = (instance.call(ALLOC, listOf(WasmValue.I32(input.size))).singleOrNull() as? WasmValue.I32)?.v
        if (ptr == null || ptr == 0) throw WasmException("$ALLOC gave no memory")
        memory.write(ptr, input)
        return ptr
    }

    private fun RunOutput.checked(): PluginResult {
        val problem =
            when {
                prompt == null && message == null -> "gave neither a prompt nor a message"
                prompt != null && (prompt.isBlank() || prompt.length > MAX_PROMPT) -> "gave an empty or too long prompt"
                message != null && message.length > MAX_MESSAGE -> "gave a message over $MAX_MESSAGE characters"
                else -> null
            }
        problem?.let { throw PluginException("Plugin '${manifest.id}' $it") }
        // The message is shown as is, so control characters other than line breaks are dropped.
        return PluginResult(prompt, message?.filter { !it.isISOControl() || it == '\n' })
    }

    // The engine reports a misbehaving guest in many ways; each must end as this plugin's failure,
    // never as a crash of the screen that ran it.
    @Suppress("TooGenericExceptionCaught")
    private fun <T> guarded(block: () -> T): T =
        try {
            block()
        } catch (e: Exception) {
            throw PluginException("Plugin '${manifest.id}' failed: ${e.message ?: e::class.simpleName}", e)
        }

    @Serializable
    private data class RunInput(
        val action: String,
        val task: PluginTask,
    )

    @Serializable
    private data class RunOutput(
        val prompt: String? = null,
        val message: String? = null,
    )

    @Serializable
    private data class AbiOnly(
        val abi: Int,
    )

    companion object {
        const val MAX_PROMPT = 20_000
        const val MAX_MESSAGE = 2_000
        private const val ALLOC = "octo_alloc"
        private const val MANIFEST = "octo_manifest"
        private const val RUN = "octo_run"
        private const val MAX_OUTPUT = 1 shl 20
        private const val MAX_MANIFEST = 64 shl 10

        private val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }

        /** Instantiates [module] and reads its manifest; throws [PluginException] for anything Octo cannot use. */
        internal fun instantiate(
            file: String,
            module: ByteArray,
            runtime: WasmRuntime,
            fuel: Long,
        ): Plugin {
            val (instance, text) = open(module, runtime, fuel)
            return Plugin(parseManifest(text), file, instance, fuel)
        }

        // The engine reports a module it cannot run in many ways; each one means "not a plugin".
        @Suppress("TooGenericExceptionCaught")
        private fun open(
            module: ByteArray,
            runtime: WasmRuntime,
            fuel: Long,
        ): Pair<WasmInstance, String> =
            try {
                // A start function runs guest code too, so instantiation is metered like a call.
                val instance = runtime.instantiate(module, imports = emptyList(), fuel = fuel)
                listOf(ALLOC, RUN).firstOrNull { !instance.hasExport(it) }?.let { throw WasmException("no $it export") }
                instance.setFuel(fuel)
                instance to readPacked(instance.memory, instance.i64(MANIFEST), MAX_MANIFEST).decodeToString()
            } catch (e: Exception) {
                throw PluginException(e.message ?: e::class.simpleName ?: "not a plugin", e)
            }

        // The ABI is read on its own first: a newer plugin's manifest may not fit this one's shape.
        private fun parseManifest(text: String): PluginManifest {
            val abi = decode(AbiOnly.serializer(), text).abi
            if (abi != PluginManifest.ABI) {
                throw PluginException("it needs plugin ABI $abi; this Octo speaks ${PluginManifest.ABI}")
            }
            val manifest = decode(PluginManifest.serializer(), text)
            manifest.problem()?.let { throw PluginException("its manifest is not valid: $it") }
            return manifest
        }

        private fun <T> decode(
            serializer: KSerializer<T>,
            text: String,
        ): T =
            try {
                json.decodeFromString(serializer, text)
            } catch (e: SerializationException) {
                throw PluginException("its manifest is not valid: ${e.message}", e)
            }

        private fun WasmInstance.i64(
            export: String,
            args: List<WasmValue> = emptyList(),
        ): Long =
            (call(export, args).singleOrNull() as? WasmValue.I64)?.v ?: throw WasmException("$export: expected an i64")
    }
}
