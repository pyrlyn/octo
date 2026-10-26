package octo.adapters.base

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import octo.core.agent.AgentEvent
import octo.core.agent.AgentSessionRef
import octo.core.agent.PermissionKind
import octo.core.agent.PermissionOption
import octo.core.agent.PlanEntry
import octo.core.agent.RequestResponse
import octo.core.agent.Role
import octo.core.agent.SessionSpec
import octo.core.agent.StopReason
import octo.core.agent.ToolStatus
import octo.core.process.RunningProcess
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * An Agent Client Protocol v1 client session (schema `schema-v1.26.0`). Octo offers no file system
 * or terminal capabilities, so the agent uses its own tools and asks only for permissions.
 * Subclasses add agent extensions through [onExtensionRequest] and [onExtensionNotification].
 */
open class AcpSession(
    process: RunningProcess,
    context: CoroutineContext,
    private val config: AcpConfig,
) : JsonRpcAgentSession(process, context) {
    private val lock = Mutex()
    private var sessionId: String? = null
    private var replaying = false
    private var turn: Job? = null
    private val tools = mutableMapOf<String, AgentEvent.ToolCall>()
    private val message = StringBuilder()
    private val thought = StringBuilder()

    protected suspend fun sessionId(): String = lock.withLock { sessionId } ?: error("No ACP session")

    override suspend fun initialize(spec: SessionSpec) {
        val id =
            withTimeout(HANDSHAKE_TIMEOUT_MS) {
                val init = peer.request("initialize", initializeParams()).obj() ?: JsonObject(emptyMap())
                val methods = init.array("authMethods").mapNotNull { it.obj()?.string("id") }
                val login = config.loginMethods.firstOrNull { it in methods }
                try {
                    openSession(spec, init)
                } catch (e: JsonRpcException) {
                    if (e.code != AUTH_REQUIRED || login == null) throw e
                    // Only methods that reuse the CLI's own login: Octo never sends a key or token.
                    peer.request("authenticate", buildJsonObject { put("methodId", login) })
                    openSession(spec, init)
                }
            }
        lock.withLock { sessionId = id }
        spec.options[OPTION_MODE]?.let { mode ->
            peer.request(
                "session/set_mode",
                buildJsonObject {
                    put("sessionId", id)
                    put("modeId", mode)
                },
            )
        }
        emit(AgentEvent.SessionReady(AgentSessionRef(id)))
    }

    private fun initializeParams() =
        buildJsonObject {
            put("protocolVersion", PROTOCOL_VERSION)
            putJsonObject("clientCapabilities") {
                putJsonObject("fs") {
                    put("readTextFile", false)
                    put("writeTextFile", false)
                }
                put("terminal", false)
            }
            putJsonObject("clientInfo") {
                put("name", "octo")
                put("title", "Octo")
                put("version", config.clientVersion)
            }
        }

    private suspend fun openSession(
        spec: SessionSpec,
        init: JsonObject,
    ): String {
        val resume = spec.resume?.value
        val params =
            buildJsonObject {
                if (resume != null) put("sessionId", resume)
                put("cwd", spec.workingDir)
                putJsonArray("mcpServers") {}
            }
        val caps = init.obj("agentCapabilities")
        return when {
            resume == null -> {
                peer.request("session/new", params).obj()?.string("sessionId")
                    ?: error("ACP agent returned no session id")
            }

            caps?.obj("sessionCapabilities")?.obj("resume") != null -> {
                peer.request("session/resume", params)
                resume
            }

            caps?.bool("loadSession") == true -> {
                // `session/load` replays the whole history; Octo keeps its own transcript, so the
                // replay is dropped instead of shown twice.
                lock.withLock { replaying = true }
                try {
                    peer.request("session/load", params)
                } finally {
                    lock.withLock { replaying = false }
                }
                resume
            }

            else -> {
                error("This agent cannot resume sessions")
            }
        }
    }

    override suspend fun prompt(text: String) {
        val id = sessionId()
        emit(AgentEvent.TurnStarted)
        // `session/prompt` answers only when the turn ends, so the turn runs in the background.
        val job =
            scope.launch {
                val params =
                    buildJsonObject {
                        put("sessionId", id)
                        putJsonArray("prompt") {
                            add(
                                buildJsonObject {
                                    put("type", "text")
                                    put("text", text)
                                },
                            )
                        }
                    }
                val end =
                    try {
                        val reason = peer.request("session/prompt", params).obj()?.string("stopReason")
                        AgentEvent.TurnEnded(stopReason(reason), null, null)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: JsonRpcException) {
                        AgentEvent.TurnEnded(StopReason.Error, null, e.message)
                    }
                flush()
                emit(end)
            }
        lock.withLock { turn = job }
    }

    // A turn whose reply arrived just before the agent exited still reports its end before
    // the session reports the exit.
    override suspend fun readOutput(lines: Flow<String>) {
        try {
            super.readOutput(lines)
        } finally {
            lock.withLock { turn }?.join()
        }
    }

    override suspend fun cancel() {
        val id = lock.withLock { sessionId } ?: return
        peer.notify("session/cancel", buildJsonObject { put("sessionId", id) })
        // The spec requires every pending permission request to be answered as cancelled.
        requests.withdrawAll()
    }

    override suspend fun onRequest(
        id: String,
        method: String,
        params: JsonElement?,
    ): JsonElement {
        val p = params.obj() ?: JsonObject(emptyMap())
        if (method != "session/request_permission") return onExtensionRequest(id, method, p)
        flush()
        val call = p.obj("toolCall")
        val known = lock.withLock { call?.string("toolCallId")?.let { tools[it] } }
        val options = p.array("options").mapNotNull { permissionOption(it.obj()) }
        val title = call?.string("title") ?: known?.title ?: "Allow a tool call"
        val response = requests.ask(id, AgentEvent.PermissionRequested(id, title, options))
        val chosen = (response as? RequestResponse.Option)?.optionId?.takeIf { pick -> options.any { it.id == pick } }
        return buildJsonObject {
            putJsonObject("outcome") {
                if (chosen == null) {
                    put("outcome", "cancelled")
                } else {
                    put("outcome", "selected")
                    put("optionId", chosen)
                }
            }
        }
    }

    /** Agent requests outside ACP v1. Octo declares no fs or terminal support, so those land here too. */
    protected open suspend fun onExtensionRequest(
        id: String,
        method: String,
        params: JsonObject,
    ): JsonElement = throw JsonRpcException(JsonRpcException.METHOD_NOT_FOUND, "Octo does not handle $method")

    protected open suspend fun onExtensionNotification(
        method: String,
        params: JsonObject,
    ) {}

    override suspend fun onNotification(
        method: String,
        params: JsonElement?,
    ) {
        val p = params.obj() ?: return
        when {
            method != "session/update" -> onExtensionNotification(method, p)
            !lock.withLock { replaying } -> p.obj("update")?.let { update(it) }
        }
    }

    private suspend fun update(update: JsonObject) {
        when (update.string("sessionUpdate")) {
            "agent_message_chunk" -> chunk(update, message)?.let { emit(AgentEvent.MessageDelta(Role.Assistant, it)) }
            "agent_thought_chunk" -> chunk(update, thought)
            "tool_call", "tool_call_update" -> toolCall(update)
            "plan" -> plan(update)
        }
    }

    private suspend fun chunk(
        update: JsonObject,
        into: StringBuilder,
    ): String? {
        val text = update.obj("content")?.takeIf { it.string("type") == "text" }?.string("text") ?: return null
        // A thought ends where the message starts and the other way round.
        if (into === message) flushThought() else flushMessage()
        lock.withLock { into.append(text) }
        return text
    }

    private suspend fun toolCall(update: JsonObject) {
        val id = update.string("toolCallId") ?: return
        flush()
        val event =
            lock.withLock {
                val known = tools[id]
                AgentEvent
                    .ToolCall(
                        id = id,
                        title = update.string("title") ?: known?.title ?: "Tool",
                        kind = update.string("kind") ?: known?.kind ?: "other",
                        status = toolStatus(update.string("status")) ?: known?.status ?: ToolStatus.Pending,
                        detail = detail(update) ?: known?.detail,
                    ).also { tools[id] = it }
            }
        emit(event)
    }

    private fun plan(update: JsonObject) {
        val entries =
            update.array("entries").mapNotNull { it.obj() }.map {
                PlanEntry(it.string("content").orEmpty(), done = it.string("status") == "completed")
            }
        emit(AgentEvent.PlanUpdated(entries))
    }

    /** Emits buffered message and thought text as whole events. */
    protected suspend fun flush() {
        flushThought()
        flushMessage()
    }

    private suspend fun flushMessage() {
        val text = lock.withLock { message.toString().also { message.clear() } }
        if (text.isNotEmpty()) emit(AgentEvent.Message(Role.Assistant, text))
    }

    private suspend fun flushThought() {
        val text = lock.withLock { thought.toString().also { thought.clear() } }
        if (text.isNotEmpty()) emit(AgentEvent.Thought(text))
    }

    companion object {
        const val PROTOCOL_VERSION = 1

        /** A session mode id from the agent's `modes`, set with `session/set_mode`. */
        const val OPTION_MODE = "mode"

        /** ACP `auth_required`. */
        const val AUTH_REQUIRED = -32000

        private const val HANDSHAKE_TIMEOUT_MS = 60_000L
        private const val DETAIL_LIMIT = 4_000

        private val PERMISSION_KINDS =
            mapOf(
                "allow_once" to PermissionKind.AllowOnce,
                "allow_always" to PermissionKind.AllowAlways,
                "reject_once" to PermissionKind.RejectOnce,
                "reject_always" to PermissionKind.RejectAlways,
            )

        private fun permissionOption(o: JsonObject?): PermissionOption? {
            val id = o?.string("optionId")
            val kind = PERMISSION_KINDS[o?.string("kind")]
            if (id == null || kind == null) return null
            return PermissionOption(id, o.string("name") ?: id, kind)
        }

        private fun toolStatus(status: String?): ToolStatus? =
            when (status) {
                "pending" -> ToolStatus.Pending
                "in_progress" -> ToolStatus.InProgress
                "completed" -> ToolStatus.Completed
                "failed" -> ToolStatus.Failed
                else -> null
            }

        private fun stopReason(reason: String?): StopReason =
            when (reason) {
                "max_tokens", "max_turn_requests" -> StopReason.MaxTurns
                "refusal" -> StopReason.Refusal
                "cancelled" -> StopReason.Cancelled
                else -> StopReason.EndTurn
            }

        private fun detail(update: JsonObject): String? {
            val parts =
                update.array("content").mapNotNull { it.obj() }.mapNotNull { c ->
                    when (c.string("type")) {
                        "content" -> c.obj("content")?.string("text")
                        "diff" -> diff(c)
                        else -> null
                    }
                }
            return parts.joinToString("\n").ifEmpty { null }?.take(DETAIL_LIMIT)
        }

        private fun diff(c: JsonObject): String {
            val old =
                c
                    .string("oldText")
                    ?.lines()
                    .orEmpty()
                    .map { "-$it" }
            val new =
                c
                    .string("newText")
                    ?.lines()
                    .orEmpty()
                    .map { "+$it" }
            return (listOf("--- ${c.string("path").orEmpty()}") + old + new).joinToString("\n")
        }
    }
}

/** What an ACP adapter tells its sessions. */
data class AcpConfig(
    val clientVersion: String,
    /** `authenticate` method ids that reuse the CLI's own login, tried when a session needs auth. */
    val loginMethods: List<String> = emptyList(),
)
