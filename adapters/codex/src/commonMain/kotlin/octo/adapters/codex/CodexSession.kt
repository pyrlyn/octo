package octo.adapters.codex

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import octo.adapters.base.JsonRpcAgentSession
import octo.adapters.base.JsonRpcException
import octo.adapters.base.array
import octo.adapters.base.epochMillis
import octo.adapters.base.long
import octo.adapters.base.obj
import octo.adapters.base.string
import octo.core.agent.AgentEvent
import octo.core.agent.AgentSessionRef
import octo.core.agent.ContextParts
import octo.core.agent.LogStream
import octo.core.agent.PermissionKind
import octo.core.agent.PermissionMode
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

/**
 * Maps the Codex app-server v2 protocol (threads, turns, items) to [AgentEvent]s. Schema source:
 * `codex app-server generate-ts` from the verified CLI version.
 */
internal class CodexSession(
    process: RunningProcess,
    context: CoroutineContext,
    private val clientVersion: String,
) : JsonRpcAgentSession(process, context, withVersionField = false) {
    private val lock = Mutex()
    private var threadId: String? = null
    private var turnId: String? = null

    /** Turns started by this process, oldest first; what a rollback can name. */
    private var turns = listOf<String>()
    private var usage: Usage? = null

    /** Set by a live switch and sent with the next turn, which is where Codex takes it. */
    private var model: String? = null
    private var permissions: CodexPermissions? = null

    override suspend fun initialize(spec: SessionSpec) {
        val thread =
            withTimeout(HANDSHAKE_TIMEOUT_MS) {
                peer.request(
                    "initialize",
                    buildJsonObject {
                        putJsonObject("clientInfo") {
                            put("name", "octo")
                            put("title", "Octo")
                            put("version", clientVersion)
                        }
                        put("capabilities", JsonNull)
                    },
                )
                peer.notify("initialized")
                val resume = spec.resume
                val params =
                    buildJsonObject {
                        if (resume != null) put("threadId", resume.value)
                        put("cwd", spec.workingDir)
                        spec.options[CodexAdapter.OPTION_MODEL]?.let { put("model", it) }
                        CodexPermissions.of(spec.permissionMode).putThread(this)
                    }
                val method =
                    when {
                        resume == null -> "thread/start"
                        spec.fork -> "thread/fork"
                        else -> "thread/resume"
                    }
                peer.request(method, params)
            }
        val id = thread.obj()?.obj("thread")?.string("id") ?: error("Codex returned no thread id")
        lock.withLock { threadId = id }
        emit(AgentEvent.SessionReady(AgentSessionRef(id)))
        thread.obj()?.string("model")?.let { emit(AgentEvent.StatsUpdated(SessionStats(model = it))) }
    }

    override suspend fun prompt(text: String) {
        val thread = lock.withLock { threadId } ?: error("No Codex thread")
        val (newModel, switched) =
            lock.withLock {
                usage = null
                (model to permissions).also {
                    model = null
                    permissions = null
                }
            }
        emit(AgentEvent.TurnStarted)
        val response =
            peer.request(
                "turn/start",
                buildJsonObject {
                    put("threadId", thread)
                    newModel?.let { put("model", it) }
                    switched?.putTurn(this)
                    put("input", input(text))
                },
            )
        response
            .obj()
            ?.obj("turn")
            ?.string("id")
            ?.let { started(it) }
    }

    // Both the response and the notification name the turn; whichever comes first records it.
    private suspend fun started(id: String) =
        lock.withLock {
            turnId = id
            if (turns.lastOrNull() != id) turns = turns + id
        }

    // `thread/revert` keeps the history before the named turn; the files are Octo's to restore.
    override suspend fun rollback(turns: Int) {
        val (thread, first) = lock.withLock { threadId to this.turns.getOrNull(this.turns.size - turns) }
        if (thread == null || first == null) error("This Codex process did not run that many turns")
        peer.request(
            "thread/revert",
            buildJsonObject {
                put("threadId", thread)
                put("beforeTurnId", first)
            },
        )
        lock.withLock { this.turns = this.turns.dropLast(turns) }
    }

    override suspend fun setModel(model: String) {
        lock.withLock { this.model = model }
    }

    override suspend fun setPermissionMode(mode: PermissionMode) {
        lock.withLock { permissions = CodexPermissions.of(mode) }
    }

    // `expectedTurnId` makes Codex refuse a turn that already ended, so the core queues the text.
    override suspend fun steer(text: String) {
        val (thread, turn) = lock.withLock { threadId to turnId }
        if (thread == null || turn == null) error("No Codex turn is running")
        peer.request(
            "turn/steer",
            buildJsonObject {
                put("threadId", thread)
                put("input", input(text))
                put("expectedTurnId", turn)
            },
        )
    }

    override suspend fun cancel() {
        val (thread, turn) = lock.withLock { threadId to turnId }
        if (thread == null || turn == null) return
        peer.request(
            "turn/interrupt",
            buildJsonObject {
                put("threadId", thread)
                put("turnId", turn)
            },
        )
    }

    override suspend fun onRequest(
        id: String,
        method: String,
        params: JsonElement?,
    ): JsonElement {
        val p = params.obj() ?: JsonObject(emptyMap())
        return when (method) {
            "item/commandExecution/requestApproval" -> {
                val title = p.string("command")?.let { "Run: $it" } ?: "Run a command"
                decision(requests.ask(id, permission(id, title, p)))
            }

            "item/fileChange/requestApproval" -> {
                decision(requests.ask(id, permission(id, "Apply file changes", p)))
            }

            "item/tool/requestUserInput" -> {
                answers(requests.ask(id, AgentEvent.QuestionAsked(id, questions(p))))
            }

            // Account and credential requests are the CLI's business, never Octo's.
            else -> {
                throw JsonRpcException(JsonRpcException.METHOD_NOT_FOUND, "Octo does not handle $method")
            }
        }
    }

    override suspend fun onNotification(
        method: String,
        params: JsonElement?,
    ) {
        val p = params.obj() ?: return
        when (method) {
            "turn/started" -> turnStarted(p)
            "item/agentMessage/delta" -> p.string("delta")?.let { emit(AgentEvent.MessageDelta(Role.Assistant, it)) }
            "item/started", "item/completed" -> item(p, completed = method == "item/completed")
            "turn/plan/updated" -> plan(p)
            "thread/tokenUsage/updated" -> tokenUsage(p)
            "account/rateLimits/updated" -> rateLimits(p)
            "turn/completed" -> turnCompleted(p)
            "serverRequest/resolved" -> requestId(p["requestId"])?.let { requests.withdraw(it) }
            "thread/status/changed" -> threadStatus(p)
            "error" -> protocolError(p)
        }
    }

    private fun protocolError(p: JsonObject) {
        p.obj("error")?.string("message")?.let { emit(AgentEvent.Log(LogStream.Protocol, it)) }
    }

    private suspend fun turnStarted(p: JsonObject) {
        p.obj("turn")?.string("id")?.let { started(it) }
    }

    // Octo derives working / blocked / idle from turns and open requests; only a system error
    // carries news the other events do not.
    private fun threadStatus(p: JsonObject) {
        if (p.obj("status")?.string("type") == "systemError") {
            emit(AgentEvent.Log(LogStream.Protocol, "Codex thread hit a system error"))
        }
    }

    private fun item(
        p: JsonObject,
        completed: Boolean,
    ) {
        val item = p.obj("item")
        val id = item?.string("id") ?: return
        val status = toolStatus(item.string("status"))
        when (item.string("type")) {
            "agentMessage" -> {
                // The started item is empty; its text streams as deltas and arrives whole on completion.
                if (completed) item.string("text")?.let { emit(AgentEvent.Message(Role.Assistant, it)) }
            }

            "reasoning" -> {
                val parts = item.array("summary") + item.array("content")
                val text = parts.mapNotNull { (it as? JsonPrimitive)?.content }
                if (completed && text.isNotEmpty()) emit(AgentEvent.Thought(text.joinToString("\n")))
            }

            "commandExecution" -> {
                val command = item.string("command") ?: "command"
                tool(id, "Run: $command", "execute", status, item.string("aggregatedOutput"))
            }

            "fileChange" -> {
                val changes = item.array("changes").mapNotNull { it.obj() }
                val paths = changes.mapNotNull { it.string("path") }.joinToString(", ")
                val diff = changes.mapNotNull { it.string("diff") }.joinToString("\n")
                tool(id, "Edit: $paths", "edit", status, diff.ifBlank { null })
            }

            "mcpToolCall" -> {
                tool(id, "${item.string("server")}.${item.string("tool")}", "other", status, null)
            }

            "webSearch" -> {
                tool(id, "Web search", "fetch", status, item.string("query"))
            }
        }
    }

    private fun tool(
        id: String,
        title: String,
        kind: String,
        status: ToolStatus?,
        detail: String?,
    ) {
        emit(AgentEvent.ToolCall(id, title, kind, status ?: ToolStatus.InProgress, detail?.take(DETAIL_LIMIT)))
    }

    private fun plan(p: JsonObject) {
        val entries =
            p.array("plan").mapNotNull { it.obj() }.map {
                PlanEntry(it.string("step").orEmpty(), done = it.string("status") == "completed")
            }
        emit(AgentEvent.PlanUpdated(entries))
    }

    // Codex counts cached input inside the input and the turn's output inside totalTokens.
    private fun contextParts(last: JsonObject): ContextParts {
        val cached = last.long("cachedInputTokens")
        return ContextParts(
            cacheRead = cached,
            input = last.long("inputTokens")?.let { it - (cached ?: 0) },
            output = last.long("outputTokens"),
        )
    }

    private suspend fun tokenUsage(p: JsonObject) {
        val tokens = p.obj("tokenUsage") ?: return
        val total = tokens.obj("total")
        emit(
            AgentEvent.StatsUpdated(
                SessionStats(
                    contextUsed = tokens.obj("last")?.long("totalTokens"),
                    contextWindow = tokens.long("modelContextWindow"),
                    inputTokens = total?.long("inputTokens"),
                    outputTokens = total?.long("outputTokens"),
                    cacheReadTokens = total?.long("cachedInputTokens"),
                    cacheWriteTokens = total?.long("cacheWriteInputTokens"),
                    contextParts = tokens.obj("last")?.let(::contextParts),
                ),
            ),
        )
        val last = tokens.obj("last") ?: return
        lock.withLock {
            usage =
                Usage(
                    inputTokens = last.long("inputTokens"),
                    outputTokens = last.long("outputTokens"),
                )
        }
    }

    // Most updates only say how close the limit is; one that names the limit reached means Codex stops.
    private fun rateLimits(p: JsonObject) {
        val limits = p.obj("rateLimits")?.takeIf { (it["rateLimitReachedType"] ?: JsonNull) != JsonNull } ?: return
        val windows = listOfNotNull(limits.obj("primary"), limits.obj("secondary"))
        val full = windows.filter { (it.long("usedPercent") ?: 0) >= FULL_PERCENT }.ifEmpty { windows }
        emit(AgentEvent.RateLimited(full.mapNotNull { it.long("resetsAt") }.maxOrNull()?.let(::epochMillis)))
    }

    private suspend fun turnCompleted(p: JsonObject) {
        val turn = p.obj("turn") ?: return
        val turnUsage = lock.withLock { usage.also { turnId = null } }
        val reason =
            when (turn.string("status")) {
                "interrupted" -> StopReason.Cancelled
                "failed" -> StopReason.Error
                else -> StopReason.EndTurn
            }
        val message =
            if (reason ==
                StopReason.Error
            ) {
                turn.obj("error")?.string("message") ?: "Codex turn failed"
            } else {
                null
            }
        emit(AgentEvent.TurnEnded(reason, turnUsage, message))
    }

    companion object {
        private const val FULL_PERCENT = 100
        private const val HANDSHAKE_TIMEOUT_MS = 60_000L
        private const val DETAIL_LIMIT = 4_000
        private const val ACCEPT = "accept"
        private const val ACCEPT_FOR_SESSION = "acceptForSession"
        private const val DECLINE = "decline"

        private val PERMISSION_OPTIONS =
            listOf(
                PermissionOption(ACCEPT, "Allow", PermissionKind.AllowOnce),
                PermissionOption(ACCEPT_FOR_SESSION, "Allow for this session", PermissionKind.AllowAlways),
                PermissionOption(DECLINE, "Reject", PermissionKind.RejectOnce),
            )

        private fun permission(
            id: String,
            title: String,
            p: JsonObject,
        ): AgentEvent.PermissionRequested {
            val reason = p.string("reason")
            return AgentEvent.PermissionRequested(
                id,
                if (reason ==
                    null
                ) {
                    title
                } else {
                    "$title ($reason)"
                },
                PERMISSION_OPTIONS,
            )
        }

        /** `cancel` also interrupts the turn, which is what dismissing the prompt means in Octo. */
        private fun decision(response: RequestResponse): JsonElement {
            val value =
                when (response) {
                    is RequestResponse.Option -> {
                        response.optionId.takeIf { it in setOf(ACCEPT, ACCEPT_FOR_SESSION) }
                            ?: DECLINE
                    }

                    else -> {
                        "cancel"
                    }
                }
            return buildJsonObject { put("decision", value) }
        }

        private fun questions(p: JsonObject): List<Question> =
            p.array("questions").mapNotNull { it.obj() }.mapNotNull { q ->
                val id = q.string("id") ?: return@mapNotNull null
                Question(
                    id = id,
                    text = q.string("question") ?: q.string("header").orEmpty(),
                    options = q.array("options").mapNotNull { it.obj()?.string("label") },
                )
            }

        private fun answers(response: RequestResponse): JsonElement =
            buildJsonObject {
                putJsonObject("answers") {
                    if (response is RequestResponse.Answers) {
                        response.answers.forEach { (id, answer) ->
                            putJsonObject(id) { put("answers", buildJsonArray { add(JsonPrimitive(answer)) }) }
                        }
                    }
                }
            }

        private fun toolStatus(status: String?): ToolStatus? =
            when (status) {
                "inProgress" -> ToolStatus.InProgress
                "completed" -> ToolStatus.Completed
                "failed", "declined" -> ToolStatus.Failed
                else -> null
            }

        private fun requestId(element: JsonElement?): String? = (element as? JsonPrimitive)?.content

        private fun input(text: String) =
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("type", "text")
                        put("text", text)
                        putJsonArray("text_elements") {}
                    },
                )
            }
    }
}
