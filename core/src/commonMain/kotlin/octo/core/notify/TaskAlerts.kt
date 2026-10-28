package octo.core.notify

import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.job
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

/** A task alerts at most once in this long, so an agent that keeps stopping does not flood the desktop. */
const val ALERT_COOLDOWN_MS = 20_000L

/**
 * An alert for each task that moves into Blocked, Failed or Done, sent once the task kept quiet
 * for [quietMs]. A newer alert for the task replaces a waiting one, and one whose state is gone by
 * then (answered, restarted, seen) is dropped, so a burst of events shows one notification. After
 * an alert the task's next one waits until [cooldownMs] has passed. The first state only sets the
 * baseline: what was already so at start-up is not news.
 */
fun StateFlow<OctoState>.taskAlerts(
    config: NotificationConfig,
    quietMs: Long = ALERT_QUIET_MS,
    cooldownMs: Long = ALERT_COOLDOWN_MS,
): Flow<Alert> {
    val states = this
    return channelFlow {
        val queue = AlertQueue(this, states, config, quietMs, cooldownMs)
        launch { states.collect { queue.signals.send(Signal.Changed(it)) } }
        for (signal in queue.signals) queue.handle(signal)
    }
}

private sealed interface Signal {
    data class Changed(
        val state: OctoState,
    ) : Signal

    data class Quiet(
        val task: TaskId,
        val kind: AlertKind,
        val timer: Job,
    ) : Signal

    data class Cooled(
        val task: TaskId,
    ) : Signal
}

/** The alert bookkeeping. Only [handle] touches it, on one coroutine, so the timers never race on it. */
private class AlertQueue(
    private val out: ProducerScope<Alert>,
    private val states: StateFlow<OctoState>,
    private val config: NotificationConfig,
    private val quietMs: Long,
    private val cooldownMs: Long,
) {
    val signals = Channel<Signal>(Channel.UNLIMITED)
    private var before: OctoState? = null
    private val waiting = mutableMapOf<TaskId, Job>()
    private val cooling = mutableSetOf<TaskId>()

    // Alerts whose quiet period ended while their task was cooling down.
    private val held = mutableMapOf<TaskId, AlertKind>()

    suspend fun handle(signal: Signal) {
        when (signal) {
            is Signal.Changed -> {
                changed(signal.state)
            }

            is Signal.Quiet -> {
                quiet(signal)
            }

            is Signal.Cooled -> {
                cooling.remove(signal.task)
                held.remove(signal.task)?.let { post(signal.task, it) }
            }
        }
    }

    private fun changed(state: OctoState) {
        val previous = before
        before = state
        if (previous == null) return
        for (view in state.tasks) {
            val id = view.task.id
            val kind =
                view.alertKind()?.takeIf { it != previous[id]?.alertKind() && config.allows(it) } ?: continue
            held.remove(id)
            waiting.remove(id)?.cancel()
            waiting[id] =
                out.launch {
                    delay(quietMs)
                    signals.send(Signal.Quiet(id, kind, coroutineContext.job))
                }
        }
    }

    private suspend fun quiet(signal: Signal.Quiet) {
        // A replaced timer may have signalled before it was cancelled.
        if (waiting[signal.task] !== signal.timer) return
        waiting.remove(signal.task)
        if (signal.task in cooling) held[signal.task] = signal.kind else post(signal.task, signal.kind)
    }

    private suspend fun post(
        id: TaskId,
        kind: AlertKind,
    ) {
        val view = states.value[id]?.takeIf { it.alertKind() == kind } ?: return
        out.send(view.alert(kind))
        cooling += id
        out.launch {
            delay(cooldownMs)
            signals.send(Signal.Cooled(id))
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
