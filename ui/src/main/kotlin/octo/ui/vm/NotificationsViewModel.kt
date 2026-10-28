package octo.ui.vm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import octo.core.notify.Attention
import octo.core.notify.NotificationRule
import octo.core.notify.NotificationSettings
import octo.core.notify.TaskNotification
import octo.core.orchestrator.OctoCore
import octo.core.task.TaskId

/** OS notifications for tasks that need the user while they look elsewhere; exists only with the `notifications` flag on. */
class NotificationsViewModel(
    core: OctoCore,
    private val main: MainViewModel,
    scope: CoroutineScope,
    settings: NotificationSettings,
    private val now: () -> Long = System::currentTimeMillis,
    quietMs: Long = NotificationRule.DEFAULT_QUIET_MS,
) {
    private val rule = NotificationRule(settings, quietMs)

    // Buffered, so a notification raised before the tray starts collecting is still shown.
    private val channel = Channel<TaskNotification>(Channel.BUFFERED)

    /** Notifications to show, each once. */
    val shown: Flow<TaskNotification> = channel.receiveAsFlow()

    @Volatile
    private var focused = true

    @Volatile
    private var last: TaskId? = null

    init {
        // collectLatest cancels the wait for a burst as soon as the tasks change again, and runs
        // one block at a time, so the rule always has a single caller.
        scope.launch {
            core.state.collectLatest { state ->
                rule.observe(state.tasks, now())
                while (true) {
                    val due = rule.nextDueMs() ?: break
                    delay((due - now()).coerceAtLeast(0))
                    rule.release(now(), attention()).forEach {
                        last = it.task
                        channel.send(it)
                    }
                }
            }
        }
    }

    fun windowFocused(focused: Boolean) {
        this.focused = focused
    }

    /** A click on a notification: AWT does not say which one, so the latest one's task opens. */
    fun open() {
        last?.let(main::select)
    }

    private fun attention(): Attention {
        val s = main.state.value
        return Attention(focused, s.selected.takeUnless { s.agentsOpen || s.usageOpen })
    }
}
