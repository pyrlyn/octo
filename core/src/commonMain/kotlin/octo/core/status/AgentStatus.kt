package octo.core.status

import kotlinx.serialization.Serializable
import octo.core.agent.AgentEvent
import octo.core.agent.StopReason

@Serializable
enum class AgentStatus {
    Starting,
    Working,
    Blocked,
    Idle,
    Failed,
    Stopped,

    /** Waiting for a free slot on its machine; set by the orchestrator, never by an agent. */
    Queued,
}

private enum class Phase { Starting, Idle, Working, Failed, Stopped }

/**
 * What the reducer remembers about one agent session. Open requests are kept as a set so the
 * agent stays [AgentStatus.Blocked] until every one of them is answered.
 */
@ConsistentCopyVisibility
data class AgentState private constructor(
    private val phase: Phase,
    val pendingRequests: Set<String>,
    val failure: String?,
    val closing: Boolean,
) {
    val status: AgentStatus
        get() =
            when {
                phase == Phase.Failed -> AgentStatus.Failed
                phase == Phase.Stopped -> AgentStatus.Stopped
                pendingRequests.isNotEmpty() -> AgentStatus.Blocked
                phase == Phase.Starting -> AgentStatus.Starting
                phase == Phase.Working -> AgentStatus.Working
                else -> AgentStatus.Idle
            }

    /** Marks a close the user asked for, so the coming exit reads as stopped, not failed. */
    fun closing(): AgentState = copy(closing = true)

    fun reduce(event: AgentEvent): AgentState =
        when (event) {
            is AgentEvent.SessionReady -> {
                if (phase == Phase.Starting) copy(phase = Phase.Idle) else this
            }

            AgentEvent.TurnStarted -> {
                copy(phase = Phase.Working, failure = null)
            }

            is AgentEvent.PermissionRequested -> {
                copy(
                    phase = Phase.Working,
                    pendingRequests =
                        pendingRequests + event.id,
                )
            }

            is AgentEvent.QuestionAsked -> {
                copy(phase = Phase.Working, pendingRequests = pendingRequests + event.id)
            }

            is AgentEvent.RequestResolved -> {
                copy(pendingRequests = pendingRequests - event.id)
            }

            is AgentEvent.TurnEnded -> {
                turnEnded(event)
            }

            is AgentEvent.Failed -> {
                fail(event.message)
            }

            is AgentEvent.Exited -> {
                exited(event.code)
            }

            else -> {
                this
            }
        }

    private fun turnEnded(event: AgentEvent.TurnEnded): AgentState =
        when (event.stopReason) {
            StopReason.Error, StopReason.Refusal -> fail(event.message ?: "Turn ended: ${event.stopReason}")
            else -> copy(phase = Phase.Idle, pendingRequests = emptySet())
        }

    private fun fail(message: String) = copy(phase = Phase.Failed, pendingRequests = emptySet(), failure = message)

    private fun exited(code: Int): AgentState =
        when {
            phase == Phase.Failed -> copy(pendingRequests = emptySet())
            closing || code == 0 -> copy(phase = Phase.Stopped, pendingRequests = emptySet())
            else -> fail("Agent exited with code $code")
        }

    companion object {
        val Initial = AgentState(Phase.Starting, emptySet(), failure = null, closing = false)
    }
}
