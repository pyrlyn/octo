package octo.core.orchestrator

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import octo.core.agent.AgentId
import octo.core.status.AgentStatus
import octo.core.task.NewTask
import octo.core.task.TaskId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SingleAgentTest {
    private val newTask =
        NewTask(repo = "/src/app", baseRef = "main", agent = AgentId("fake"), title = "Fix it", prompt = "Fix it")

    private val fake = FakeAdapter()
    private val other = FakeAdapter(id = AgentId("other"), displayName = "Other")

    private fun open() = (fake.sessions + other.sessions).filterNot { it.closed }

    private suspend fun TestScope.started(): Pair<Setup, TaskId> {
        val s = setup(fake, others = listOf(other))
        val id = s.core.createTask(newTask)
        testScheduler.runCurrent()
        return s to id
    }

    private fun Setup.agent(id: TaskId) =
        core.state.value[id]!!
            .task.agent

    @Test
    fun noAgentStartsWhileAnotherOfTheTaskIsOpen() =
        runTest {
            val (s, id) = started()
            val overlaps = mutableListOf<Int>()
            val count = { open().size.let { if (it > 0) overlaps += it } }
            listOf(fake, other).forEach { it.beforeStart = count }

            launch { s.core.switchAgent(id, AgentId("other"), null) }
            launch { s.core.restart(id) }
            launch { s.core.restart(id) }
            testScheduler.runCurrent()

            assertEquals(emptyList(), overlaps)
            assertEquals(1, open().size)
            assertEquals(4, fake.sessions.size + other.sessions.size)
            assertEquals(AgentId("other"), s.agent(id))
        }

    @Test
    fun aSecondSwitchWhileOneIsUnderWayIsRefused() =
        runTest {
            val (s, id) = started()
            val gate = CompletableDeferred<Unit>()
            fake.sessions.single().closeGate = gate
            launch { s.core.switchAgent(id, AgentId("other"), null) }
            testScheduler.runCurrent()

            // Bounded, so a switch that waits for the lock instead fails the test rather than hanging it.
            assertFailsWith<HandoffInProgressException> {
                withTimeout(1_000) { s.core.switchAgent(id, AgentId("fake"), null) }
            }
            gate.complete(Unit)
            testScheduler.runCurrent()

            assertEquals(1, fake.sessions.size, "the refused switch started nothing")
            assertEquals(1, other.sessions.size)
            assertEquals(AgentId("other"), s.agent(id))
            assertEquals(listOf(other.sessions.single()), open())

            s.core.switchAgent(id, AgentId("fake"), null)
            testScheduler.runCurrent()
            assertEquals(AgentId("fake"), s.agent(id), "a switch is taken again once the first is over")
            assertEquals(listOf(fake.sessions.last()), open())
        }

    @Test
    fun aFailedSwitchLeavesTheTaskOpenToTheNextOne() =
        runTest {
            val (s, id) = started()
            assertFailsWith<UnknownAgentException> { s.core.switchAgent(id, AgentId("ghost"), null) }
            assertEquals(listOf(fake.sessions.single()), open(), "the agent keeps running")

            s.core.switchAgent(id, AgentId("other"), null)
            testScheduler.runCurrent()
            assertEquals(AgentId("other"), s.agent(id))
            assertEquals(
                AgentStatus.Idle,
                s.core.state.value[id]!!
                    .status,
            )
            assertTrue(fake.sessions.single().closed)
        }
}
