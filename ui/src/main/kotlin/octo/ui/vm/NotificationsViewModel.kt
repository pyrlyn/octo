package octo.ui.vm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch
import octo.core.notify.ALERT_QUIET_MS
import octo.core.notify.Alert
import octo.core.notify.NotificationConfig
import octo.core.notify.taskAlerts
import octo.core.orchestrator.OctoCore
import octo.core.task.TaskId

/** OS notifications for tasks the user is not looking at; only built while the `notifications` flag is on. */
class NotificationsViewModel(
    core: OctoCore,
    private val main: MainViewModel,
    scope: CoroutineScope,
    config: NotificationConfig,
    quietMs: Long = ALERT_QUIET_MS,
) {
    private val posted = MutableSharedFlow<Alert>(extraBufferCapacity = BUFFER)

    /** What the window should show as OS notifications. */
    val notifications: SharedFlow<Alert> = posted.asSharedFlow()

    private val focused = MutableStateFlow(false)
    private val last = MutableStateFlow<TaskId?>(null)

    // macOS brings the app forward when its notification is clicked but tells the app nothing, so
    // the window gaining focus after a background alert counts as that click.
    private val unanswered = MutableStateFlow<TaskId?>(null)

    init {
        scope.launch {
            core.state.taskAlerts(config, quietMs).collect { alert ->
                val background = !focused.value
                if (background || !showing(alert.task)) {
                    last.value = alert.task
                    if (background) unanswered.value = alert.task
                    posted.emit(alert)
                }
            }
        }
    }

    /** The window gained or lost focus. */
    fun focused(focused: Boolean) {
        this.focused.value = focused
        if (focused) unanswered.getAndUpdate { null }?.let(main::select)
    }

    /** A notification was clicked: opens the task it was about. */
    fun clicked() {
        unanswered.value = null
        last.value?.let(main::select)
    }

    private fun showing(id: TaskId) =
        main.state.value.let {
            it.selected == id && !it.agentsOpen && !it.usageOpen
        }

    private companion object {
        const val BUFFER = 16
    }
}
