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

@Serializable
enum class Role { User, Assistant }

@Serializable
enum class ToolStatus { Pending, InProgress, Completed, Failed }

@Serializable
enum class StopReason { EndTurn, MaxTurns, Cancelled, Refusal, Error }

enum class LogStream { Stderr, Protocol }

@Serializable
enum class PermissionKind { AllowOnce, AllowAlways, RejectOnce, RejectAlways }

@Serializable
data class PermissionOption(
    val id: String,
    val label: String,
    val kind: PermissionKind,
)

@Serializable
data class Question(
    val id: String,
    val text: String,
    val options: List<String> = emptyList(),
    val multiSelect: Boolean = false,
)

@Serializable
data class PlanEntry(
    val text: String,
    val done: Boolean,
)

@Serializable
data class Usage(
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val costUsd: Double? = null,
)

sealed interface RequestResponse {
    data class Option(
        val optionId: String,
    ) : RequestResponse

    /** Answers keyed by [Question.id]. */
    data class Answers(
        val answers: Map<String, String>,
    ) : RequestResponse

    data object Cancelled : RequestResponse
}
