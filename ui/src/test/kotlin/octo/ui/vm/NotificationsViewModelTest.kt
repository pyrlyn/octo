package octo.ui.vm

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import octo.core.notify.NotificationSettings
import octo.core.notify.TaskNotification
import octo.core.status.AgentStatus
import octo.core.task.PendingRequest
import octo.core.task.TaskId
import octo.core.task.TaskView
import octo.testing.FakeCatalog
import octo.testing.FakeOctoCore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {
    private val core = FakeOctoCore()
    private val approval = PendingRequest.Permission("p", "Run", emptyList())

    private fun put(
        id: String,
        status: AgentStatus,
        unseen: Boolean = false,
    ) = core.put(
        TaskView(
            FakeOctoCore.task(TaskId(id)),
            status,
            pending = listOf(approval).filter { status == AgentStatus.Blocked },
            unseen = unseen,
        ),
    )

    @Test
    fun aTaskThatNeedsTheUserNotifiesAndAClickOpensIt() =
        runTest {
            put("a", AgentStatus.Working)
            put("b", AgentStatus.Working)
            val main = MainViewModel(core, backgroundScope)
            val vm =
                NotificationsViewModel(
                    core,
                    main,
                    backgroundScope,
                    NotificationSettings(),
                    { testScheduler.currentTime },
                    100,
                )
            val shown = mutableListOf<TaskNotification>()
            backgroundScope.launch { vm.shown.collect { shown += it } }
            testScheduler.runCurrent()

            vm.windowFocused(false)
            put("a", AgentStatus.Blocked)
            testScheduler.runCurrent()
            testScheduler.advanceTimeBy(50)
            put("a", AgentStatus.Working)
            testScheduler.runCurrent()
            put("a", AgentStatus.Blocked)
            testScheduler.runCurrent()
            testScheduler.advanceTimeBy(60)
            testScheduler.runCurrent()
            assertEquals(emptyList(), shown, "the second approval restarted the burst")
            testScheduler.advanceTimeBy(50)
            testScheduler.runCurrent()
            assertEquals(listOf("Task a: Needs your approval"), shown.map { "${it.title}: ${it.message}" })

            vm.open()
            testScheduler.runCurrent()
            assertEquals(TaskId("a"), main.state.value.selected)
            assertTrue("seen a" in core.calls, core.calls.toString())
        }

    @Test
    fun theOpenTaskInAFocusedWindowStaysQuiet() =
        runTest {
            put("a", AgentStatus.Working)
            put("b", AgentStatus.Working)
            val main = MainViewModel(core, backgroundScope)
            val vm =
                NotificationsViewModel(
                    core,
                    main,
                    backgroundScope,
                    NotificationSettings(),
                    { testScheduler.currentTime },
                    100,
                )
            val shown = mutableListOf<TaskNotification>()
            backgroundScope.launch { vm.shown.collect { shown += it } }
            main.select(TaskId("a"))
            testScheduler.runCurrent()

            put("a", AgentStatus.Idle, unseen = true)
            put("b", AgentStatus.Idle, unseen = true)
            testScheduler.advanceTimeBy(200)
            testScheduler.runCurrent()
            assertEquals(listOf(TaskId("b")), shown.map { it.task }, "the user already sees a")
        }

    @Test
    fun theFlagCreatesTheViewModel() =
        runTest {
            val catalog = FakeCatalog(core)
            assertNull(ViewModels(core, catalog, backgroundScope).notifications)
            assertNotNull(
                ViewModels(core, catalog, backgroundScope, notifications = NotificationSettings()).notifications,
            )
        }
}
