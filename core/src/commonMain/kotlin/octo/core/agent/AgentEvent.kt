package octo.core.agent

import kotlinx.serialization.Serializable

/** The vocabulary every adapter maps its agent's protocol into. */
sealed interface AgentEvent {
    data class SessionReady(
        val ref: AgentSessionRef,
    ) : AgentEvent

    data object TurnStarted : AgentEvent

    data class MessageDelta(
        val role: Role,
        val text: String,
    ) : AgentEvent

    data class Message(
        val role: Role,
        val text: String,
    ) : AgentEvent

    data class Thought(
        val text: String,
    ) : AgentEvent

    data class ToolCall(
        val id: String,
        val title: String,
        val kind: String?,
        val status: ToolStatus,
        val detail: String? = null,
    ) : AgentEvent

    data class PermissionRequested(
        val id: String,
        val title: String,
        val options: List<PermissionOption>,
    ) : AgentEvent

    data class QuestionAsked(
        val id: String,
        val questions: List<Question>,
    ) : AgentEvent

    data class RequestResolved(
        val id: String,
    ) : AgentEvent

    data class PlanUpdated(
        val entries: List<PlanEntry>,
    ) : AgentEvent

    data class TurnEnded(
        val stopReason: StopReason,
        val usage: Usage? = null,
        val message: String? = null,
    ) : AgentEvent

    /** The agent's latest model, context and token figures; see [SessionStats.merge]. */
    data class StatsUpdated(
        val stats: SessionStats,
    ) : AgentEvent

    /** The provider refused work for a usage limit; [resetsAtMs] (epoch milliseconds) is when it lifts, if said. */
    data class RateLimited(
        val resetsAtMs: Long? = null,
    ) : AgentEvent

    data class Log(
        val stream: LogStream,
        val line: String,
    ) : AgentEvent

    data class Failed(
        val message: String,
    ) : AgentEvent

    data class Exited(
        val code: Int,
    ) : AgentEvent
}

/** Who wrote a message in the transcript. */
@Serializable
enum class Role { User, Assistant }

/** The state of one tool call. */
@Serializable
enum class ToolStatus { Pending, InProgress, Completed, Failed }

/** Why a turn ended. [Error] and [Refusal] mark the task failed. */
@Serializable
enum class StopReason { EndTurn, MaxTurns, Cancelled, Refusal, Error }

/** Where a log line came from: the agent's stderr or its protocol stream. */
enum class LogStream { Stderr, Protocol }

/** The kind of answer a permission option gives. */
@Serializable
enum class PermissionKind { AllowOnce, AllowAlways, RejectOnce, RejectAlways }

/** One answer the agent offers to a permission request. */
@Serializable
data class PermissionOption(
    val id: String,
    val label: String,
    val kind: PermissionKind,
)

/** One question the agent asks, with optional fixed [options]. */
@Serializable
data class Question(
    val id: String,
    val text: String,
    val options: List<String> = emptyList(),
    val multiSelect: Boolean = false,
)

/** One step of the agent's plan. */
@Serializable
data class PlanEntry(
    val text: String,
    val done: Boolean,
)

/** Token and cost figures an agent reports; any of them may be missing. */
@Serializable
data class Usage(
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val costUsd: Double? = null,
)

/**
 * What an agent says about its session so far. Token counts are totals for the session, not
 * deltas, and a null field is one the agent did not report this time.
 */
data class SessionStats(
    val model: String? = null,
    /** Tokens currently in the context window. */
    val contextUsed: Long? = null,
    val contextWindow: Long? = null,
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val cacheReadTokens: Long? = null,
    val cacheWriteTokens: Long? = null,
    /** How long the prompt cache keeps the latest request's context, counted from its start. */
    val promptCacheTtlMs: Long? = null,
    /** What [contextUsed] is made of, when the agent says. */
    val contextParts: ContextParts? = null,
) {
    /** Agents report figures piecemeal, so a field [next] leaves out keeps its last value. */
    fun merge(next: SessionStats) =
        SessionStats(
            model = next.model ?: model,
            contextUsed = next.contextUsed ?: contextUsed,
            contextWindow = next.contextWindow ?: contextWindow,
            inputTokens = next.inputTokens ?: inputTokens,
            outputTokens = next.outputTokens ?: outputTokens,
            cacheReadTokens = next.cacheReadTokens ?: cacheReadTokens,
            cacheWriteTokens = next.cacheWriteTokens ?: cacheWriteTokens,
            promptCacheTtlMs = next.promptCacheTtlMs ?: promptCacheTtlMs,
            contextParts = next.contextParts ?: contextParts,
        )
}

/**
 * The latest request's context by kind of token; the parts add up to [SessionStats.contextUsed]
 * and a null part is one the agent does not count there.
 */
data class ContextParts(
    val cacheRead: Long? = null,
    val cacheWrite: Long? = null,
    val input: Long? = null,
    val output: Long? = null,
)

/** The user's answer to a pending permission request or question. */
sealed interface RequestResponse {
    data class Option(
        val optionId: String,
    ) : RequestResponse

    /** Answers keyed by [Question.id]; a multi-select answer joins the labels with [SEPARATOR]. */
    data class Answers(
        val answers: Map<String, String>,
    ) : RequestResponse {
        companion object {
            const val SEPARATOR = ", "
        }
    }

    data object Cancelled : RequestResponse
}
