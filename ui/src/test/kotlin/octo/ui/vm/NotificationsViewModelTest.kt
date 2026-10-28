package octo.ui.vm

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import octo.core.notify.ALERT_QUIET_MS
import octo.core.notify.Alert
import octo.core.notify.NotificationConfig
import octo.core.status.AgentStatus
import octo.core.task.TaskId
import octo.core.task.TaskView
import octo.testing.FakeCatalog
import octo.testing.FakeOctoCore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {
    private val core = FakeOctoCore()
    private val posted = mutableListOf<Alert>()

    private fun put(
        id: String,
        status: AgentStatus,
        unseen: Boolean = false,
    ) = core.put(TaskView(FakeOctoCore.task(TaskId(id)), status, unseen = unseen))

    private fun TestScope.start(): Pair<MainViewModel, NotificationsViewModel> {
        put("a", AgentStatus.Working)
        put("b", AgentStatus.Working)
        val main = MainViewModel(core, backgroundScope)
        val vm = NotificationsViewModel(core, main, backgroundScope, NotificationConfig())
        backgroundScope.launch { vm.notifications.toList(posted) }
        runCurrent()
        return main to vm
    }

    private fun TestScope.settle() {
        runCurrent()
        advanceTimeBy(ALERT_QUIET_MS + 1)
        runCurrent()
    }

    private fun selected() = core.state.value.layout.selectedTask

    @Test
    fun anAlertInTheBackgroundOpensItsTaskWhenTheWindowComesBack() =
        runTest {
            val (_, vm) = start()
            put("a", AgentStatus.Idle, unseen = true)
            settle()
            assertEquals(listOf("a"), posted.map { it.task.value })
            vm.focused(true)
            runCurrent()
            assertEquals(TaskId("a"), selected(), "macOS activates the app on a click and says nothing else")
            vm.focused(false)
            vm.focused(true)
            runCurrent()
            assertEquals(listOf("seen a"), core.calls, "only the first focus after the alert opens the task")
        }

    @Test
    fun theOpenTaskInAFocusedWindowStaysQuiet() =
        runTest {
            val (main, vm) = start()
            vm.focused(true)
            main.select(TaskId("a"))
            runCurrent()
            put("a", AgentStatus.Blocked)
            put("b", AgentStatus.Failed)
            settle()
            assertEquals(listOf("b"), posted.map { it.task.value }, "only the task out of sight")
            vm.focused(false)
            vm.focused(true)
            runCurrent()
            assertEquals(TaskId("a"), selected(), "an alert seen while focused does not move the window")
            vm.clicked()
            runCurrent()
            assertEquals(TaskId("b"), selected(), "a click opens the alerted task")
        }

    @Test
    fun anotherScreenHidesTheOpenTask() =
        runTest {
            val (main, vm) = start()
            vm.focused(true)
            main.select(TaskId("a"))
            main.openAgents()
            runCurrent()
            put("a", AgentStatus.Idle, unseen = true)
            settle()
            assertEquals(listOf("a"), posted.map { it.task.value })
        }

    @Test
    fun theFlagReachesTheWindow() =
        runTest {
            val catalog = FakeCatalog(core)
            assertNull(ViewModels(core, catalog, backgroundScope).notifications)
            assertNotNull(
                ViewModels(core, catalog, backgroundScope, notificationConfig = NotificationConfig()).notifications,
            )
        }
}
