package octo.ui.vm

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import octo.core.agent.AgentId
import octo.core.agent.VendorTerms
import octo.core.status.AgentStatus
import octo.core.task.TaskGroup
import octo.core.task.TaskId
import octo.core.task.TaskView
import octo.testing.FakeCatalog
import octo.testing.FakeOctoCore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CompareViewModelTest {
    private val core = FakeOctoCore()
    private val demo = AgentId("demo")

    private fun TestScope.settle() = testScheduler.runCurrent()

    private fun TestScope.ready(compare: Boolean = true): NewTaskViewModel {
        val vm = NewTaskViewModel(core, FakeCatalog(core), backgroundScope, compare = compare)
        settle()
        vm.useCompare(true)
        vm.editRepo("/src/octo")
        testScheduler.advanceTimeBy(NewTaskUiState.BRANCH_LOOKUP_DELAY_MS)
        settle()
        vm.editPrompt("Fix the login bug")
        return vm
    }

    private fun creates() = core.calls.filter { it.startsWith("create") }

    @Test
    fun comparingIsHiddenWithoutTheFlag() =
        runTest {
            val vm = ready(compare = false)
            assertFalse(vm.state.value.compareEnabled)
            assertFalse(vm.state.value.compare)
        }

    @Test
    fun everyModelGetsItsOwnTaskAfterTheTerms() =
        runTest {
            core.termsPending = VendorTerms("1", "Read these", "https://example.com/terms")
            val vm = ready()
            vm.toggleCompared(demo)
            assertFalse(vm.state.value.canCreate, "one pick compares with nothing")
            vm.toggleCompared(AgentId("missing"))
            assertFalse(vm.state.value.canCreate, "an agent that is not installed cannot run")
            vm.toggleCompared(AgentId("missing"))
            vm.editComparedModels(demo, " small, large ,small,")
            assertEquals(
                listOf("small", "large"),
                vm.state.value.picks
                    .map { it.model },
            )
            assertTrue(vm.state.value.canCreate)

            vm.create()
            settle()
            assertNotNull(vm.state.value.terms)
            assertTrue(creates().isEmpty(), "nothing starts before the terms are settled")

            vm.acceptTerms()
            settle()
            assertEquals(TaskId("t1"), vm.state.value.created)
            assertEquals(
                listOf(
                    "create Fix the login bug · demo (small) Fix the login bug",
                    "create Fix the login bug · demo (large) Fix the login bug",
                ),
                creates(),
            )
            val groups =
                core.state.value.tasks
                    .map { it.task.group }
            assertEquals(1, groups.distinct().size)
            assertEquals("Fix the login bug", groups.first()?.title)
        }

    @Test
    fun aPickThatFailsKeepsTheFormOpen() =
        runTest {
            core.failingModels += "gone"
            val vm = ready()
            vm.toggleCompared(demo)
            vm.editComparedModels(demo, "small, gone")
            vm.create()
            settle()
            assertNull(vm.state.value.created)
            assertEquals("Started 1 of 2. Not started: demo (gone): No such model", vm.state.value.error)
            assertEquals(1, creates().size)
        }

    @Test
    fun aComparisonIsListedTogetherUnderItsTitle() =
        runTest {
            val group = TaskGroup("g", "Fix login")

            fun put(
                id: String,
                group: TaskGroup? = null,
                parent: String? = null,
            ) = core.put(
                TaskView(
                    FakeOctoCore.task(TaskId(id)).copy(group = group, parentId = parent?.let(::TaskId)),
                    AgentStatus.Idle,
                ),
            )
            put("a", group)
            put("x")
            put("b", group)
            put("b1", parent = "b")
            val main = MainViewModel(core, backgroundScope)
            settle()
            assertEquals(
                listOf(
                    Triple("a", 1, "Fix login"),
                    Triple("b", 1, null),
                    Triple("b1", 2, null),
                    Triple("x", 0, null),
                ),
                main.state.value.groups
                    .single()
                    .tasks
                    .map { Triple(it.id.value, it.depth, it.groupTitle) },
            )
        }
}
