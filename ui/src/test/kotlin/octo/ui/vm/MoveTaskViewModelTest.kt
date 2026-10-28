package octo.ui.vm

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import octo.core.status.AgentStatus
import octo.core.task.TaskId
import octo.core.task.TaskView
import octo.testing.FakeCatalog
import octo.testing.FakeOctoCore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MoveTaskViewModelTest {
    private val core = FakeOctoCore()
    private val id = TaskId("a")

    init {
        core.put(TaskView(FakeOctoCore.task(id), AgentStatus.Idle))
    }

    @Test
    fun theOnlyTargetIsPickedAndTheMoveRuns() =
        runTest {
            val vm = MoveTaskViewModel(core, id, backgroundScope)
            assertEquals(listOf<String?>("box"), vm.state.value.targets)
            assertTrue(vm.state.value.canMove)

            vm.move()
            testScheduler.runCurrent()

            assertTrue(vm.state.value.done)
            assertEquals(
                "box",
                core.state.value[id]!!
                    .task.machine,
            )
            assertTrue("move box" in core.calls)
        }

    @Test
    fun thisComputerIsATargetToo() =
        runTest {
            core.moveTargets = listOf(null, "cave")
            val vm = MoveTaskViewModel(core, id, backgroundScope)
            assertFalse(vm.state.value.canMove, "nothing picked among several")

            vm.select(null)
            vm.move()
            testScheduler.runCurrent()

            assertTrue("move here" in core.calls)
        }

    @Test
    fun aFailedMoveShowsWhyAndCanBeTriedAgain() =
        runTest {
            core.moveFailure = "Connection refused"
            val vm = MoveTaskViewModel(core, id, backgroundScope)

            vm.move()
            testScheduler.runCurrent()

            assertEquals("Connection refused", vm.state.value.error)
            assertFalse(vm.state.value.done)
            assertTrue(vm.state.value.canMove)
            assertNull(
                core.state.value[id]!!
                    .task.machine,
            )
        }

    @Test
    fun theFlagGatesTheActionAndItsCommand() =
        runTest {
            val catalog = FakeCatalog(core)
            val off = ViewModels(core, catalog, backgroundScope, palette = true)
            assertNull(off.moveTask(id))
            assertFalse(off.canMove)

            val on = ViewModels(core, catalog, backgroundScope, palette = true, moveTask = true)
            on.main.select(id)
            testScheduler.runCurrent()
            val palette = on.palette()!!
            testScheduler.runCurrent()
            assertTrue(PaletteItem.Run(Command.Move) in palette.state.value.items)
            on.main.run(Command.Move)
            assertEquals(TaskDialog.Move, on.main.state.value.taskDialog)
        }
}
