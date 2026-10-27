package octo.core.orchestrator

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import octo.core.status.AgentStatus
import octo.core.task.OctoState
import octo.core.task.TaskPlace
import octo.core.task.TaskView
import kotlin.time.Clock

private val NEEDS_USER = setOf(AgentStatus.Blocked, AgentStatus.Failed)

/** Back among the active tasks when the status change ends its snooze; see [TaskPlace.Snoozed]. */
internal fun TaskView.wokenBy(
    before: AgentStatus,
    after: AgentStatus,
): TaskView {
    val place = task.place as? TaskPlace.Snoozed ?: return this
    val needsUser = before != after && after in NEEDS_USER
    val turnEnded = before != AgentStatus.Idle && after == AgentStatus.Idle
    return if (needsUser || (turnEnded && place.untilMs == null)) awake() else this
}

/** Waits for the earliest snooze to run out; any change to the snoozes starts the wait over. */
internal suspend fun MutableStateFlow<OctoState>.wakeSnoozed(clock: Clock) {
    map { state -> state.tasks.mapNotNull { (it.task.place as? TaskPlace.Snoozed)?.untilMs }.minOrNull() }
        .distinctUntilChanged()
        .collectLatest { until ->
            until ?: return@collectLatest
            delay(until - clock.now().toEpochMilliseconds())
            val now = clock.now().toEpochMilliseconds()
            update { state ->
                state.copy(
                    tasks =
                        state.tasks.map { view ->
                            val due = (view.task.place as? TaskPlace.Snoozed)?.untilMs?.let { it <= now } == true
                            if (due) view.awake() else view
                        },
                )
            }
        }
}

private fun TaskView.awake() = copy(task = task.copy(place = TaskPlace.Active))
