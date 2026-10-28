package octo.core.notify

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import octo.core.task.OctoState
import octo.core.task.PendingRequest
import octo.core.task.SidebarStatus
import octo.core.task.TaskId
import octo.core.task.TaskView

/** Which task events raise an OS notification; used only with the `notifications` flag on. */
@Serializable
data class NotificationConfig(
    /** The agent waits for an approval or an answer. */
    val blocked: Boolean = true,
    val failed: Boolean = true,
    /** The agent finished its turn. */
    val done: Boolean = true,
) {
    fun allows(kind: AlertKind) =
        when (kind) {
            AlertKind.Blocked -> blocked
            AlertKind.Failed -> failed
            AlertKind.Done -> done
        }
}

enum class AlertKind { Blocked, Failed, Done }

/** What one OS notification says about a task. */
data class Alert(
    val task: TaskId,
    val kind: AlertKind,
    val title: String,
    val message: String,
)

/** Long enough for an agent's quick approve-and-continue or fail-and-retry to settle first. */
const val ALERT_QUIET_MS = 1_500L

private const val MAX_FAILURE = 200

/**
 * An alert for each task that moves into Blocked, Failed or Done, sent once the task kept quiet
 * for [quietMs]. A newer alert for the task replaces a waiting one, and one whose state is gone by
 * then (answered, restarted, seen) is dropped, so a burst of events shows one notification. The
 * first state only sets the baseline: what was already so at start-up is not news.
 */
fun StateFlow<OctoState>.taskAlerts(
    config: NotificationConfig,
    quietMs: Long = ALERT_QUIET_MS,
): Flow<Alert> {
    val states = this
    return channelFlow {
        var before: OctoState? = null
        // Touched only by the collector, so the timers below never race on it.
        val waiting = mutableMapOf<TaskId, Job>()
        states.collect { state ->
            val previous = before
            before = state
            if (previous == null) return@collect
            val news =
                state.tasks.mapNotNull { view ->
                    view
                        .alertKind()
                        ?.takeIf { it != previous[view.task.id]?.alertKind() && config.allows(it) }
                        ?.let { view.task.id to it }
                }
            for ((id, kind) in news) {
                waiting.remove(id)?.cancel()
                waiting[id] =
                    launch {
                        delay(quietMs)
                        states.value[id]?.takeIf { it.alertKind() == kind }?.let { send(it.alert(kind)) }
                    }
            }
        }
    }
}

private fun TaskView.alertKind() =
    when (sidebarStatus) {
        SidebarStatus.Blocked -> AlertKind.Blocked
        SidebarStatus.Failed -> AlertKind.Failed
        SidebarStatus.Done -> AlertKind.Done
        else -> null
    }

private fun TaskView.alert(kind: AlertKind) = Alert(task.id, kind, task.title, message(kind))

private fun TaskView.message(kind: AlertKind) =
    when {
        kind == AlertKind.Done -> "Finished its turn"
        kind == AlertKind.Failed -> failure?.let { "Failed: ${it.take(MAX_FAILURE)}" } ?: "Failed"
        pending.firstOrNull() is PendingRequest.Questions -> "Has a question for you"
        else -> "Waits for your approval"
    }
