package octo.ui.vm

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import octo.core.status.AgentStatus
import octo.core.task.Checkpoint
import octo.core.task.TaskId
import octo.core.task.TaskView
import octo.core.task.TranscriptEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class RevertViewModelTest {
    private val core = FakeOctoCore()
    private val id = TaskId("t1")
    private val checkpoint = TranscriptEntry.Checkpoint(1, "refs/octo/t1/1")

    @Test
    fun aRevertIsConfirmedFirstAndOnlyOfferedWithTheFlag() =
        runTest {
            val task = FakeOctoCore.task(id).copy(checkpoints = listOf(Checkpoint(1, checkpoint.ref, "run")))
            core.put(TaskView(task, AgentStatus.Idle))
            val hidden = TaskViewModel(core, id, backgroundScope)
            val vm = TaskViewModel(core, id, backgroundScope, checkpoints = true)
            testScheduler.runCurrent()
            assertEquals(emptySet(), hidden.state.value.revertable, "the checkpoints flag is off")
            assertEquals(setOf(checkpoint.ref), vm.state.value.revertable)

            vm.askRevert(checkpoint)
            vm.askRevert(null)
            vm.revert()
            testScheduler.runCurrent()
            assertEquals(emptyList(), core.calls, "a cancelled revert does nothing")

            vm.askRevert(checkpoint)
            assertEquals(checkpoint, vm.state.value.reverting)
            vm.revert()
            testScheduler.runCurrent()
            assertEquals(listOf("revert t1 ${checkpoint.ref}"), core.calls)
            assertNull(vm.state.value.reverting)
        }
}
