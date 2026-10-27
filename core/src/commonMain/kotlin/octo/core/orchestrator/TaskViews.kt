package octo.core.orchestrator

import octo.core.agent.AgentEvent
import octo.core.persist.PersistedState
import octo.core.persist.PersistedTask
import octo.core.persist.StateStore
import octo.core.status.AgentState
import octo.core.status.AgentStatus
import octo.core.task.OctoState
import octo.core.task.PendingRequest
import octo.core.task.TaskId
import octo.core.task.TaskView
import octo.core.task.Transcript
import octo.core.task.TranscriptEntry

/** The view after [event], given the reducer's state [reduced] after the same event. */
internal fun TaskView.next(
    event: AgentEvent,
    reduced: AgentState,
): TaskView {
    val ref = (event as? AgentEvent.SessionReady)?.ref ?: task.sessionRef
    val opened =
        when (event) {
            is AgentEvent.PermissionRequested -> {
                pending +
                    PendingRequest.Permission(event.id, event.title, event.options)
            }

            is AgentEvent.QuestionAsked -> {
                pending + PendingRequest.Questions(event.id, event.questions)
            }

            else -> {
                pending
            }
        }
    val seen =
        when (event) {
            AgentEvent.TurnStarted -> false
            is AgentEvent.TurnEnded -> unseen || reduced.status == AgentStatus.Idle
            else -> unseen
        }
    return copy(
        task = task.copy(sessionRef = ref, lastStatus = reduced.status),
        status = reduced.status,
        // The reducer owns the open set, so a request it closed disappears here too.
        pending = opened.filter { it.id in reduced.pendingRequests },
        failure = reduced.failure,
        unseen = seen,
        stats = (event as? AgentEvent.StatsUpdated)?.let { stats.merge(it.stats) } ?: stats,
    )
}

internal fun persistedState(
    state: OctoState,
    transcripts: Map<TaskId, List<TranscriptEntry>>,
) = PersistedState(
    tasks =
        state.tasks.map { view ->
            val tail = Transcript.settled(transcripts[view.task.id].orEmpty()).takeLast(StateStore.TRANSCRIPT_TAIL)
            PersistedTask(view.task, tail)
        },
    layout = state.layout,
)
