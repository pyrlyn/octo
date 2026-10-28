package octo.core.notify

import kotlinx.serialization.Serializable
import octo.core.task.PendingRequest
import octo.core.task.SidebarStatus
import octo.core.task.TaskId
import octo.core.task.TaskView

/** Which task events show an OS notification; used only with the `notifications` flag on. */
@Serializable
data class NotificationSettings(
    /** The agent waits on an approval or an answer. */
    val blocked: Boolean = true,
    val failed: Boolean = true,
    /** The agent finished its turn. */
    val done: Boolean = true,
) {
    fun allows(event: NotificationEvent): Boolean =
        when (event) {
            NotificationEvent.Blocked -> blocked
            NotificationEvent.Failed -> failed
            NotificationEvent.Done -> done
        }
}

enum class NotificationEvent { Blocked, Failed, Done }

data class TaskNotification(
    val task: TaskId,
    val event: NotificationEvent,
    val title: String,
    val message: String,
)

/** Where the user is looking; a task they can already see needs no notification. */
data class Attention(
    val windowFocused: Boolean,
    val openTask: TaskId?,
)

/**
 * Picks the task events that notify. A task's events come in bursts (several approvals in a row,
 * a failure followed by the exit), so each burst waits until it has been quiet for [quietMs] and
 * then notifies once, with the task's latest event; a task notifies at most once per [cooldownMs].
 * Not thread-safe: one caller feeds it.
 */
class NotificationRule(
    private val settings: NotificationSettings,
    private val quietMs: Long = DEFAULT_QUIET_MS,
    private val cooldownMs: Long = DEFAULT_COOLDOWN_MS,
) {
    private class Burst(
        val view: TaskView,
        val event: NotificationEvent,
        val changedMs: Long,
    )

    private var previous: Map<TaskId, SidebarStatus>? = null
    private val bursts = mutableMapOf<TaskId, Burst>()
    private val shownMs = mutableMapOf<TaskId, Long>()

    /** Feeds the latest tasks. The first call only records them, so restarting Octo notifies nothing. */
    fun observe(
        tasks: List<TaskView>,
        nowMs: Long,
    ) {
        val before = previous
        previous = tasks.associate { it.task.id to it.sidebarStatus }
        if (before == null) return
        val ids = tasks.map { it.task.id }.toSet()
        bursts.keys.retainAll(ids)
        shownMs.keys.retainAll(ids)
        for (view in tasks) {
            val id = view.task.id
            if (view.sidebarStatus == before[id]) {
                // Same status, newer details (a renamed task, a second request): the latest one is shown.
                bursts[id]?.let { bursts[id] = Burst(view, it.event, it.changedMs) }
                continue
            }
            // A burst that ends where nothing awaits the user, such as an answered approval, is dropped.
            val event = view.sidebarStatus.event()?.takeIf(settings::allows)
            if (event == null) bursts.remove(id) else bursts[id] = Burst(view, event, nowMs)
        }
    }

    /** When the next burst is due, or null when none is waiting. */
    fun nextDueMs(): Long? = bursts.entries.minOfOrNull { (id, burst) -> dueMs(id, burst) }

    /** Ends the bursts that are due; those for the task the user is looking at end silently. */
    fun release(
        nowMs: Long,
        attention: Attention,
    ): List<TaskNotification> {
        val due = bursts.entries.filter { (id, burst) -> dueMs(id, burst) <= nowMs }.map { it.key to it.value }
        return due.mapNotNull { (id, burst) ->
            bursts.remove(id)
            if (attention.windowFocused && attention.openTask == id) return@mapNotNull null
            shownMs[id] = nowMs
            TaskNotification(id, burst.event, burst.view.task.title, message(burst))
        }
    }

    private fun dueMs(
        id: TaskId,
        burst: Burst,
    ): Long = maxOf(burst.changedMs + quietMs, shownMs[id]?.plus(cooldownMs) ?: Long.MIN_VALUE)

    private fun message(burst: Burst): String =
        when (burst.event) {
            NotificationEvent.Blocked -> {
                if (burst.view.pending.any { it is PendingRequest.Permission }) {
                    "Needs your approval"
                } else {
                    "Has a question for you"
                }
            }

            NotificationEvent.Failed -> {
                burst.view.failure?.let { "Failed: $it" } ?: "Failed"
            }

            NotificationEvent.Done -> {
                "Finished its turn"
            }
        }

    companion object {
        const val DEFAULT_QUIET_MS = 2_000L
        const val DEFAULT_COOLDOWN_MS = 20_000L
    }
}

private fun SidebarStatus.event(): NotificationEvent? =
    when (this) {
        SidebarStatus.Blocked -> NotificationEvent.Blocked
        SidebarStatus.Failed -> NotificationEvent.Failed
        SidebarStatus.Done -> NotificationEvent.Done
        else -> null
    }
