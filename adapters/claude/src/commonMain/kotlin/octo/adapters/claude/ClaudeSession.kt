package octo.adapters.claude

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import octo.adapters.base.ProcessAgentSession
import octo.adapters.base.array
import octo.adapters.base.bool
import octo.adapters.base.double
import octo.adapters.base.long
import octo.adapters.base.obj
import octo.adapters.base.parseJsonObject
import octo.adapters.base.plusTokens
import octo.adapters.base.string
import octo.core.agent.AgentEvent
import octo.core.agent.AgentSessionRef
import octo.core.agent.LogStream
import octo.core.agent.PermissionKind
import octo.core.agent.PermissionOption
import octo.core.agent.PlanEntry
import octo.core.agent.Question
import octo.core.agent.RequestResponse
import octo.core.agent.Role
import octo.core.agent.SessionSpec
import octo.core.agent.SessionStats
import octo.core.agent.StopReason
import octo.core.agent.ToolStatus
import octo.core.agent.Usage
import octo.core.process.RunningProcess
import kotlin.coroutines.CoroutineContext

/** Maps Claude's stream-json messages and control requests to [AgentEvent]s. */
internal class ClaudeSession(
    process: RunningProcess,
    context: CoroutineContext,
    private val ref: AgentSessionRef,
) : ProcessAgentSession(process, context) {
    private sealed interface Pending {
        val input: JsonObject

        class Permission(
            override val input: JsonObject,
            val suggestions: JsonArray,
        ) : Pending

        class Questions(
            override val input: JsonObject,
        ) : Pending
    }

    private val lock = Mutex()
    private var totals = SessionStats()
    private val outgoing = mutableMapOf<String, CompletableDeferred<JsonObject>>()
    private val pending = mutableMapOf<String, Pending>()
    private val tools = mutableMapOf<String, AgentEvent.ToolCall>()
    private var nextRequest = 1
    private var cancelling = false

    override suspend fun initialize(spec: SessionSpec) {
        withTimeout(INITIALIZE_TIMEOUT_MS) {
            control(
                buildJsonObject {
                    put("subtype", "initialize")
                    put("hooks", JsonNull)
                },
            )
        }
        emit(AgentEvent.SessionReady(ref))
    }

    override suspend fun prompt(text: String) {
        lock.withLock { cancelling = false }
        emit(AgentEvent.TurnStarted)
        send {
            put("type", "user")
            put("session_id", "")
            put(
                "message",
                buildJsonObject {
                    put("role", "user")
                    put("content", text)
                },
            )
            put("parent_tool_use_id", JsonNull)
        }
    }

    override suspend fun respond(
        requestId: String,
        response: RequestResponse,
    ) {
        val request = lock.withLock { pending.remove(requestId) } ?: return
        val decision =
            when (request) {
                is Pending.Permission -> permissionDecision(request, response)
                is Pending.Questions -> questionDecision(request, response)
            }
        send {
            put("type", "control_response")
            put(
                "response",
                buildJsonObject {
                    put("subtype", "success")
                    put("request_id", requestId)
                    put("response", decision)
                },
            )
        }
        emit(AgentEvent.RequestResolved(requestId))
    }

    override suspend fun cancel() {
        lock.withLock { cancelling = true }
        withTimeout(INTERRUPT_TIMEOUT_MS) { control(buildJsonObject { put("subtype", "interrupt") }) }
    }

    override suspend fun setModel(model: String) {
        withTimeout(INTERRUPT_TIMEOUT_MS) {
            control(
                buildJsonObject {
                    put("subtype", "set_model")
                    put("model", model)
                },
            )
        }
    }

    override suspend fun readOutput(lines: Flow<String>) {
        lines.collect { line ->
            if (line.isBlank()) return@collect
            val message = parseJsonObject(line)
            if (message == null) {
                emit(AgentEvent.Log(LogStream.Protocol, line))
            } else {
                handle(message)
            }
        }
        val waiting = lock.withLock { outgoing.values.toList().also { outgoing.clear() } }
        waiting.forEach { it.completeExceptionally(IllegalStateException("Claude exited")) }
    }

    private suspend fun handle(message: JsonObject) {
        when (message.string("type")) {
            "system" -> if (message.string("subtype") == "init") system(message)
            "stream_event" -> streamEvent(message)
            "assistant" -> assistant(message)
            "user" -> toolResults(message)
            "result" -> result(message)
            "control_request" -> controlRequest(message)
            "control_cancel_request" -> controlCancel(message)
            "control_response" -> controlResponse(message)
        }
    }

    private fun system(message: JsonObject) {
        message.string("model")?.let { emit(AgentEvent.StatsUpdated(SessionStats(model = it))) }
        val id = message.string("session_id") ?: return
        // A resumed or forked session can come back under a new id; the core keeps the latest.
        if (id != ref.value) emit(AgentEvent.SessionReady(AgentSessionRef(id)))
    }

    private fun streamEvent(message: JsonObject) {
        val delta = message.obj("event")?.takeIf { it.string("type") == "content_block_delta" }?.obj("delta") ?: return
        if (delta.string("type") == "text_delta") {
            delta.string("text")?.let { emit(AgentEvent.MessageDelta(Role.Assistant, it)) }
        }
    }

    private suspend fun assistant(message: JsonObject) {
        // A subagent's messages carry its own context, not the session's.
        if (message.string("parent_tool_use_id") == null) contextUsed(message.obj("message")?.obj("usage"))
        for (block in message.obj("message")?.array("content").orEmpty()) {
            val content = block.obj() ?: continue
            when (content.string("type")) {
                "text" -> {
                    content.string("text")?.let { emit(AgentEvent.Message(Role.Assistant, it)) }
                }

                "thinking" -> {
                    content
                        .string(
                            "thinking",
                        )?.takeIf { it.isNotBlank() }
                        ?.let { emit(AgentEvent.Thought(it)) }
                }

                "tool_use" -> {
                    toolUse(content)
                }
            }
        }
    }

    // Every request resends the whole context, so the latest prompt size is what the window holds.
    private fun contextUsed(usage: JsonObject?) {
        val parts = listOf("input_tokens", "cache_read_input_tokens", "cache_creation_input_tokens")
        val used = parts.mapNotNull { usage?.long(it) }.takeIf { it.isNotEmpty() }?.sum() ?: return
        emit(AgentEvent.StatsUpdated(SessionStats(contextUsed = used)))
    }

    private suspend fun toolUse(content: JsonObject) {
        val id = content.string("id") ?: return
        val name = content.string("name") ?: "tool"
        val input = content.obj("input") ?: JsonObject(emptyMap())
        if (name == "TodoWrite") {
            val entries =
                input.array("todos").mapNotNull { it.obj() }.map {
                    PlanEntry(it.string("content").orEmpty(), done = it.string("status") == "completed")
                }
            emit(AgentEvent.PlanUpdated(entries))
        }
        val call =
            AgentEvent.ToolCall(
                id,
                toolTitle(name, input),
                toolKind(name),
                ToolStatus.InProgress,
                // Write and Edit carry whole files in their input.
                input.toString().take(DETAIL_LIMIT),
            )
        lock.withLock { tools[id] = call }
        emit(call)
    }

    private suspend fun toolResults(message: JsonObject) {
        val results =
            message.obj("message")?.array("content").orEmpty().mapNotNull { block ->
                block.obj()?.takeIf { it.string("type") == "tool_result" && it.string("tool_use_id") != null }
            }
        for (content in results) {
            val id = content.string("tool_use_id").orEmpty()
            val started =
                lock.withLock { tools.remove(id) } ?: AgentEvent.ToolCall(id, "Tool", null, ToolStatus.InProgress)
            val status = if (content.bool("is_error") == true) ToolStatus.Failed else ToolStatus.Completed
            emit(started.copy(status = status, detail = resultText(content["content"])))
        }
    }

    private suspend fun result(message: JsonObject) {
        val subtype = message.string("subtype")
        val cancelled = lock.withLock { cancelling.also { cancelling = false } }
        // An API failure can arrive as subtype "success" with is_error set, so both are checked.
        val failed = message.bool("is_error") == true || subtype?.startsWith("error") == true
        val reason =
            when {
                cancelled -> StopReason.Cancelled
                subtype == "error_max_turns" -> StopReason.MaxTurns
                failed -> StopReason.Error
                else -> StopReason.EndTurn
            }
        val usage = message.obj("usage")
        turnStats(usage, message.obj("modelUsage"))
        emit(
            AgentEvent.TurnEnded(
                stopReason = reason,
                usage =
                    Usage(
                        inputTokens = usage?.long("input_tokens"),
                        outputTokens = usage?.long("output_tokens"),
                        costUsd = message.double("total_cost_usd"),
                    ),
                message = if (reason == StopReason.Error) errorText(message) else null,
            ),
        )
    }

    private suspend fun turnStats(
        usage: JsonObject?,
        models: JsonObject?,
    ) {
        val turn =
            SessionStats(
                inputTokens = usage?.long("input_tokens"),
                outputTokens = usage?.long("output_tokens"),
                cacheReadTokens = usage?.long("cache_read_input_tokens"),
                cacheWriteTokens = usage?.long("cache_creation_input_tokens"),
            )
        val window = models?.values?.mapNotNull { it.obj()?.long("contextWindow") }?.maxOrNull()
        val stats = lock.withLock { totals.plusTokens(turn).also { totals = it } }
        emit(AgentEvent.StatsUpdated(stats.copy(contextWindow = window)))
    }

    private suspend fun controlRequest(message: JsonObject) {
        val id = message.string("request_id")
        val request = message.obj("request")
        if (id == null || request == null) return
        when {
            request.string("subtype") == "can_use_tool" -> {
                canUseTool(id, request)
            }

            // Octo registers no hooks or SDK MCP servers, so nothing else is expected; refuse it.
            else -> {
                send {
                    put("type", "control_response")
                    put(
                        "response",
                        buildJsonObject {
                            put("subtype", "error")
                            put("request_id", id)
                            put("error", "Unsupported control request: ${request.string("subtype")}")
                        },
                    )
                }
            }
        }
    }

    private suspend fun canUseTool(
        id: String,
        request: JsonObject,
    ) {
        val toolName = request.string("tool_name") ?: "tool"
        val input = request.obj("input") ?: JsonObject(emptyMap())
        if (toolName == ASK_USER_QUESTION) {
            lock.withLock { pending[id] = Pending.Questions(input) }
            emit(AgentEvent.QuestionAsked(id, questions(input)))
        } else {
            val suggestions = request["permission_suggestions"] as? JsonArray ?: JsonArray(emptyList())
            lock.withLock { pending[id] = Pending.Permission(input, suggestions) }
            val title = request.string("title") ?: toolTitle(toolName, input)
            emit(AgentEvent.PermissionRequested(id, title, permissionOptions(suggestions.isNotEmpty())))
        }
    }

    private suspend fun controlCancel(message: JsonObject) {
        val id = message.string("request_id") ?: return
        if (lock.withLock { pending.remove(id) } != null) emit(AgentEvent.RequestResolved(id))
    }

    private suspend fun controlResponse(message: JsonObject) {
        val response = message.obj("response") ?: return
        val reply = lock.withLock { response.string("request_id")?.let(outgoing::remove) } ?: return
        if (response.string("subtype") == "error") {
            reply.completeExceptionally(IllegalStateException(response.string("error") ?: "Control request failed"))
        } else {
            reply.complete(response.obj("response") ?: JsonObject(emptyMap()))
        }
    }

    private suspend fun control(request: JsonObject): JsonObject {
        val reply = CompletableDeferred<JsonObject>()
        val id = lock.withLock { "req_${nextRequest++}".also { outgoing[it] = reply } }
        send {
            put("type", "control_request")
            put("request_id", id)
            put("request", request)
        }
        return reply.await()
    }

    private suspend fun send(body: JsonObjectBuilder.() -> Unit) {
        process.writeLine(buildJsonObject(body).toString())
    }

    private fun permissionDecision(
        request: Pending.Permission,
        response: RequestResponse,
    ): JsonObject =
        buildJsonObject {
            when {
                response is RequestResponse.Option && response.optionId == ALLOW_ALWAYS -> {
                    put("behavior", "allow")
                    put("updatedInput", request.input)
                    put("updatedPermissions", request.suggestions)
                }

                response is RequestResponse.Option && response.optionId == ALLOW -> {
                    put("behavior", "allow")
                    put("updatedInput", request.input)
                }

                response is RequestResponse.Cancelled -> {
                    put("behavior", "deny")
                    put("message", "The user cancelled the request")
                    put("interrupt", true)
                }

                else -> {
                    put("behavior", "deny")
                    put("message", "The user rejected this action")
                }
            }
        }

    private fun questionDecision(
        request: Pending.Questions,
        response: RequestResponse,
    ): JsonObject =
        buildJsonObject {
            if (response is RequestResponse.Answers) {
                put("behavior", "allow")
                put(
                    "updatedInput",
                    buildJsonObject {
                        put("questions", request.input["questions"] ?: JsonArray(emptyList()))
                        put("answers", buildJsonObject { response.answers.forEach { (q, a) -> put(q, a) } })
                    },
                )
            } else {
                put("behavior", "deny")
                put("message", "The user dismissed the question")
            }
        }

    companion object {
        const val ALLOW = "allow"
        const val ALLOW_ALWAYS = "allow_always"
        const val REJECT = "reject"
        private const val ASK_USER_QUESTION = "AskUserQuestion"
        private const val INITIALIZE_TIMEOUT_MS = 60_000L
        private const val INTERRUPT_TIMEOUT_MS = 10_000L
        private const val DETAIL_LIMIT = 4_000

        fun permissionOptions(canRemember: Boolean): List<PermissionOption> =
            buildList {
                add(PermissionOption(ALLOW, "Allow", PermissionKind.AllowOnce))
                if (canRemember) add(PermissionOption(ALLOW_ALWAYS, "Always allow", PermissionKind.AllowAlways))
                add(PermissionOption(REJECT, "Reject", PermissionKind.RejectOnce))
            }

        /** Claude keys answers by the question text, so that text doubles as the question id. */
        fun questions(input: JsonObject): List<Question> =
            input.array("questions").mapNotNull { it.obj() }.mapNotNull { q ->
                val text = q.string("question") ?: return@mapNotNull null
                Question(
                    id = text,
                    text = text,
                    options = q.array("options").mapNotNull { it.obj()?.string("label") },
                    multiSelect = q.bool("multiSelect") == true,
                )
            }

        fun toolTitle(
            name: String,
            input: JsonObject,
        ): String {
            val subject = TITLE_FIELDS.firstNotNullOfOrNull { input.string(it) }
            return if (subject == null) name else "$name: $subject"
        }

        fun toolKind(name: String): String =
            when (name) {
                "Bash", "PowerShell" -> "execute"
                "Read" -> "read"
                "Write", "Edit", "MultiEdit", "NotebookEdit" -> "edit"
                "Grep", "Glob" -> "search"
                "WebFetch", "WebSearch" -> "fetch"
                else -> "other"
            }

        private val TITLE_FIELDS = listOf("command", "file_path", "pattern", "url", "query", "description")

        private fun resultText(content: JsonElement?): String? {
            val text =
                when (content) {
                    is JsonPrimitive -> content.content
                    is JsonArray -> content.mapNotNull { it.obj()?.string("text") }.joinToString("\n")
                    else -> null
                }
            return text?.take(DETAIL_LIMIT)
        }

        private fun errorText(message: JsonObject): String {
            val errors = message.array("errors").mapNotNull { (it as? JsonPrimitive)?.content }
            return errors.joinToString("; ").ifBlank { null }
                ?: message.string("result")?.ifBlank { null }
                ?: message.string("subtype")
                ?: "Claude reported an error"
        }
    }
}
