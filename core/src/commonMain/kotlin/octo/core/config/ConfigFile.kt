package octo.core.config

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import octo.core.agent.PermissionMode
import octo.core.io.AtomicFile

/** `config.json` as it was last read, with what Octo will make of it at its next start. */
data class ConfigSnapshot(
    /** Null while there is no file yet. */
    val text: String?,
    val config: OctoConfig,
    /** Why the file is being ignored, in [OctoConfig.parse]'s words. */
    val warning: String?,
) {
    /** Entries Octo will refuse or ignore, in the same words it uses at start-up. */
    val problems: List<String>
        get() =
            listOfNotNull(config.remoteAccess.problem()) +
                config.machines.mapNotNull { it.problem() } +
                config.agentLimits().warnings
}

class InvalidConfigException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Edits `config.json` for the settings screen. An edit touches only its own key, so keys Octo
 * does not know (a newer version's, a plugin's) survive; a file that is not a JSON object is
 * never overwritten, since that would throw away whatever the user was in the middle of.
 */
class ConfigFile(
    private val file: AtomicFile,
) {
    private val lock = Mutex()

    val path: String get() = file.path.toString()

    suspend fun load(): ConfigSnapshot = snapshot(file.read())

    suspend fun update(edit: ConfigEdit): ConfigSnapshot =
        lock.withLock {
            val text = file.read()
            val updated = edit.apply(tree(text))
            val written = json.encodeToString(JsonObject.serializer(), updated) + "\n"
            file.write(written)
            snapshot(written)
        }

    /** Puts every setting back to its default by emptying the file. */
    suspend fun reset(): ConfigSnapshot =
        lock.withLock {
            file.write(EMPTY)
            snapshot(EMPTY)
        }

    private fun snapshot(text: String?): ConfigSnapshot =
        OctoConfig.parse(text).let { ConfigSnapshot(text, it.config, it.warning) }

    private fun tree(text: String?): JsonObject {
        if (text.isNullOrBlank()) return JsonObject(emptyMap())
        val parsed =
            try {
                json.parseToJsonElement(text)
            } catch (e: SerializationException) {
                throw InvalidConfigException("config.json is not valid JSON, so it was left alone: ${e.message}", e)
            }
        return parsed as? JsonObject
            ?: throw InvalidConfigException("config.json is not a JSON object, so it was left alone")
    }

    internal companion object {
        val json = Json { prettyPrint = true }
        private const val EMPTY = "{}\n"
    }
}

/** One change to `config.json`'s JSON, keeping every key it does not set. */
fun interface ConfigEdit {
    fun apply(tree: JsonObject): JsonObject

    companion object {
        fun flag(
            id: String,
            on: Boolean,
        ) = ConfigEdit { it.with("flags", it.child("flags").with(id, JsonPrimitive(on))) }

        fun defaultPermissionMode(mode: PermissionMode) =
            ConfigEdit { it.with("defaultPermissionMode", JsonPrimitive(mode.name)) }

        /** A null [command] removes the repository's entry. */
        fun worktreeSetup(
            repo: String,
            command: String?,
        ) = ConfigEdit { tree ->
            tree.with("worktreeSetup", tree.child("worktreeSetup").with(repo, command?.let(::JsonPrimitive)))
        }

        fun remotePort(port: Int) =
            ConfigEdit { it.with("remoteAccess", it.child("remoteAccess").with("port", JsonPrimitive(port))) }

        /**
         * Replaces the machine called [previousId] with [machine], or adds it when [previousId] is
         * null; a null [machine] removes it.
         */
        fun machine(
            previousId: String?,
            machine: MachineConfig?,
        ) = ConfigEdit { tree ->
            val machines = (tree["machines"] as? JsonArray).orEmpty()
            val encoded = machine?.let { ConfigFile.json.encodeToJsonElement(MachineConfig.serializer(), it) }
            val at = machines.indexOfFirst { (it as? JsonObject)?.get("id") == JsonPrimitive(previousId) }
            val updated =
                when {
                    previousId == null || at < 0 -> machines + listOfNotNull(encoded)
                    encoded == null -> machines.filterIndexed { i, _ -> i != at }
                    else -> machines.mapIndexed { i, old -> if (i == at) encoded else old }
                }
            tree.with("machines", JsonArray(updated))
        }

        fun appearance(appearance: AppearanceConfig) =
            ConfigEdit {
                it.with(
                    "appearance",
                    ConfigFile.json.encodeToJsonElement(AppearanceConfig.serializer(), appearance),
                )
            }
    }
}

private fun JsonObject.child(key: String): JsonObject = (this[key] as? JsonObject) ?: JsonObject(emptyMap())

/** This object with [key] set to [value], or without it when [value] is null; other keys keep their order. */
private fun JsonObject.with(
    key: String,
    value: JsonElement?,
): JsonObject = JsonObject(if (value == null) this - key else this + (key to value))
