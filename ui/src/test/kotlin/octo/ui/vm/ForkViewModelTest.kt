package octo.ui.vm

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import octo.core.status.AgentStatus
import octo.core.task.TaskId
import octo.core.task.TaskView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ForkViewModelTest {
    private val core = FakeOctoCore()

    private fun put(
        id: String,
        parent: String? = null,
    ) = core.put(
        TaskView(FakeOctoCore.task(TaskId(id)).copy(parentId = parent?.let(::TaskId)), AgentStatus.Idle),
    )

    private fun MainViewModel.rows() =
        state.value.groups
            .single()
            .tasks
            .map { it.id.value to it.depth }

    @Test
    fun forksSitUnderTheirParent() =
        runTest {
            put("a")
            put("b")
            put("a1", parent = "a")
            put("a1x", parent = "a1")
            put("orphan", parent = "gone")
            put("loop", parent = "loop")
            val main = MainViewModel(core, backgroundScope)
            testScheduler.runCurrent()
            assertEquals(
                listOf("a" to 0, "a1" to 1, "a1x" to 2, "b" to 0, "orphan" to 0, "loop" to 0),
                main.rows(),
            )
        }

    @Test
    fun forkingOpensTheFork() =
        runTest {
            put("a")
            val main = MainViewModel(core, backgroundScope)
            main.fork(TaskId("a"))
            testScheduler.runCurrent()
            assertTrue("fork a" in core.calls)
            assertEquals(TaskId("t1"), main.state.value.selected)
            assertEquals(listOf("a" to 0, "t1" to 1), main.rows())
        }

    @Test
    fun theActionExistsOnlyWithTheFlag() =
        runTest {
            val catalog = FakeCatalog(core)
            assertFalse(ViewModels(core, catalog, backgroundScope).canFork)
            assertTrue(ViewModels(core, catalog, backgroundScope, fork = true).canFork)
        }
}
