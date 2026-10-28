package octo.adapters.zcode

import kotlinx.serialization.json.JsonObject
import octo.adapters.base.bool
import octo.adapters.base.long
import octo.adapters.base.obj
import octo.adapters.base.parseJsonObject
import octo.adapters.base.string
import octo.core.agent.AgentEvent
import octo.core.agent.Role
import octo.core.agent.SessionStats
import octo.core.agent.StopReason
import octo.core.agent.ToolStatus
import octo.core.agent.Usage

/**
 * Maps one `zcode --prompt --output-format stream-json` run: one ZCode protocol session event per
 * line (`type`, `sessionId`, `payload`), then a closing `type: "result"` line with the answer and
 * usage. Unknown event types are skipped, as ZCode's own docs ask of consumers.
 */
internal class ZCodeTurn(
    private val emit: (AgentEvent) -> Unit,
    private val onSession: suspend (String) -> Unit,
) {
    private val tools = mutableMapOf<String, AgentEvent.ToolCall>()
    private val message = StringBuilder()
    private val thought = StringBuilder()
    private var streamed = false
    private var finished = false
    private var stop: StopReason? = null
    private var error: String? = null

    /** This run's tokens and context figures, from its `result` line. */
    var stats: SessionStats? = null
        private set

    suspend fun read(line: String) {
        val event = parseJsonObject(line) ?: return
        event.string("sessionId")?.let { onSession(it) }
        val payload = event.obj("payload") ?: JsonObject(emptyMap())
        when (event.string("type")) {
            "model.streaming" -> {
                streaming(payload)
            }

            "tool.updated" -> {
                tool(payload)
            }

            "turn.completed" -> {
                stop = stopReason(payload.string("resultType"))
            }

            "turn.failed" -> {
                stop = StopReason.Error
                error = payload.obj("error")?.string("message")
            }

            "result" -> {
                result(event)
            }
        }
    }

    private fun streaming(payload: JsonObject) {
        val delta = payload.string("delta")?.takeIf { it.isNotEmpty() } ?: return
        when (payload.string("kind")) {
            "text_delta" -> {
                flushThought()
                message.append(delta)
                streamed = true
                emit(AgentEvent.MessageDelta(Role.Assistant, delta))
            }

            "reasoning_delta" -> {
                flushMessage()
                thought.append(delta)
            }
        }
    }

    private fun tool(payload: JsonObject) {
        val id = payload.string("toolCallId")
        val status = toolStatus(payload)
        if (id == null || status == null) return
        flush()
        val known = tools[id]
        val name = payload.string("toolName") ?: known?.title ?: "Tool"
        val detail =
            when (payload.string("kind")) {
                "scheduled" -> payload["input"]?.toString()
                "result" -> payload.obj("result")?.string("content")
                "error" -> payload.obj("error")?.string("message")
                else -> null
            }
        val call =
            AgentEvent.ToolCall(
                id = id,
                title = name,
                kind = known?.kind ?: toolKind(name),
                status = status,
                detail = detail?.take(DETAIL_LIMIT) ?: known?.detail,
            )
        tools[id] = call
        emit(call)
    }

    private fun result(event: JsonObject) {
        val usage = event.obj("usage")
        val projection = event.obj("projection")
        stats =
            SessionStats(
                inputTokens = usage?.long("inputTokens"),
                outputTokens = usage?.long("outputTokens"),
                cacheReadTokens = usage?.long("cacheReadTokens"),
                cacheWriteTokens = usage?.long("cacheWriteTokens"),
                contextUsed = projection?.long("contextUsed"),
                contextWindow = projection?.long("contextWindow"),
            )
        // Without text deltas (a local slash command, say) the answer is only here.
        if (!streamed) event.string("response")?.let { message.append(it) }
        finished = true
    }

    /** The turn's end once its process exited with [exitCode]. */
    fun end(
        exitCode: Int,
        cancelled: Boolean,
        lastError: String?,
    ): AgentEvent.TurnEnded {
        flush()
        val usage = stats?.let { Usage(it.inputTokens, it.outputTokens) }
        return when {
            cancelled -> {
                AgentEvent.TurnEnded(StopReason.Cancelled, usage)
            }

            finished -> {
                AgentEvent.TurnEnded(stop ?: StopReason.EndTurn, usage, error)
            }

            else -> {
                AgentEvent.TurnEnded(
                    StopReason.Error,
                    usage,
                    error ?: lastError ?: "ZCode exited with code $exitCode",
                )
            }
        }
    }

    private fun flush() {
        flushThought()
        flushMessage()
    }

    private fun flushMessage() {
        if (message.isNotEmpty()) emit(AgentEvent.Message(Role.Assistant, message.toString()))
        message.clear()
    }

    private fun flushThought() {
        if (thought.isNotEmpty()) emit(AgentEvent.Thought(thought.toString()))
        thought.clear()
    }

    private companion object {
        const val DETAIL_LIMIT = 4_000

        fun toolStatus(payload: JsonObject): ToolStatus? =
            when (payload.string("kind")) {
                "scheduled" -> {
                    ToolStatus.Pending
                }

                "started" -> {
                    ToolStatus.InProgress
                }

                "result" -> {
                    if (payload.obj("result")?.bool("success") ==
                        false
                    ) {
                        ToolStatus.Failed
                    } else {
                        ToolStatus.Completed
                    }
                }

                "error" -> {
                    ToolStatus.Failed
                }

                else -> {
                    null
                }
            }

        fun stopReason(resultType: String?): StopReason =
            when (resultType) {
                null, "success" -> StopReason.EndTurn
                "cancelled" -> StopReason.Cancelled
                "error_max_turns" -> StopReason.MaxTurns
                else -> StopReason.Error
            }

        // The ACP tool kinds the UI already knows, for ZCode's built-in tool names.
        fun toolKind(name: String): String =
            when (name.lowercase()) {
                "read" -> "read"
                "write", "edit", "multiedit" -> "edit"
                "bash" -> "execute"
                "grep", "glob" -> "search"
                "webfetch", "websearch" -> "fetch"
                else -> "other"
            }
    }
}
