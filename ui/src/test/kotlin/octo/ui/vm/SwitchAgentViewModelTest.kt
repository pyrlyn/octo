package octo.ui.vm

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import octo.core.agent.AgentId
import octo.core.agent.MODEL_OPTION
import octo.core.agent.VendorTerms
import octo.core.status.AgentStatus
import octo.core.task.TaskId
import octo.core.task.TaskView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SwitchAgentViewModelTest {
    private val core = FakeOctoCore()
    private val id = TaskId("a")

    private fun put(machine: String? = null) =
        core.put(
            TaskView(
                FakeOctoCore.task(id).copy(machine = machine, options = mapOf(MODEL_OPTION to "small")),
                AgentStatus.Idle,
            ),
        )

    @Test
    fun theSameAgentSwitchesOnlyItsModel() =
        runTest {
            put()
            val vm = SwitchAgentViewModel(core, FakeCatalog(core), id, backgroundScope)
            testScheduler.runCurrent()
            val start = vm.state.value
            assertEquals(AgentId("demo"), start.agent)
            assertEquals("small", start.model)
            assertFalse(start.canSwitch, "nothing changed yet")

            vm.selectAgent(AgentId("missing"))
            assertFalse(vm.state.value.canSwitch, "an agent that is not installed cannot take over")
            vm.selectAgent(AgentId("demo"))
            vm.editModel(" large ")
            assertFalse(vm.state.value.handsOver)
            vm.switch()
            testScheduler.runCurrent()
            assertEquals(listOf("switch demo large"), core.calls)
            assertTrue(vm.state.value.done)
        }

    @Test
    fun anotherAgentStartsWithoutAModelAndAsksForItsTerms() =
        runTest {
            put(machine = "box")
            core.termsPending = VendorTerms("1", "Read these", "https://example.com/terms")
            val catalog = FakeCatalog(core)
            val vm = SwitchAgentViewModel(core, catalog, id, backgroundScope)
            testScheduler.runCurrent()
            assertEquals(
                listOf(AgentId("claude")),
                vm.state.value.agents
                    .map { it.id },
                "the task's machine is asked",
            )

            vm.selectAgent(AgentId("claude"))
            assertEquals("", vm.state.value.model)
            assertTrue(vm.state.value.handsOver)
            vm.switch()
            testScheduler.runCurrent()
            assertNotNull(vm.state.value.terms)
            assertTrue(core.calls.isEmpty())

            vm.acceptTerms()
            testScheduler.runCurrent()
            assertEquals(listOf(AgentId("claude")), catalog.acknowledged)
            assertEquals(listOf("switch claude"), core.calls)
        }

    @Test
    fun theActionExistsOnlyWithTheFlag() =
        runTest {
            put()
            val catalog = FakeCatalog(core)
            val off = ViewModels(core, catalog, backgroundScope)
            assertFalse(off.canSwitchAgent)
            assertNull(off.switchAgent(id))
            val on = ViewModels(core, catalog, backgroundScope, handoff = true)
            assertTrue(on.canSwitchAgent)
            assertNotNull(on.switchAgent(id))
        }
}
