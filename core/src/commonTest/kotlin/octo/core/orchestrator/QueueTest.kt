package octo.core.orchestrator

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import octo.core.agent.AdapterRegistry
import octo.core.agent.AgentEvent
import octo.core.agent.AgentId
import octo.core.flags.FeatureFlags
import octo.core.status.AgentStatus
import octo.core.task.NewTask
import octo.core.task.TaskId
import octo.core.task.TranscriptEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class QueueTest {
    private suspend fun TestScope.ids(limits: Map<String?, Int>): Setup {
        var next = 0
        return setup(newId = { "id${++next}" }, agentLimits = limits)
    }

    private suspend fun TestScope.task(
        s: Setup,
        prompt: String? = null,
        machine: String? = null,
    ): TaskId {
        val id = s.core.createTask(NewTask("/src/app", "main", AgentId("fake"), "Fix", prompt, machine = machine))
        testScheduler.runCurrent()
        return id
    }

    private fun Setup.status(id: TaskId) = core.state.value[id]?.status

    @Test
    fun aTaskOverTheLimitWaitsWithItsPromptsUntilASlotFrees() =
        runTest {
            val s = ids(mapOf(null to 1))
            val first = task(s)
            val second = task(s, prompt = "Go")
            s.core.sendPrompt(second, "And more")

            assertEquals(AgentStatus.Queued, s.status(second))
            assertEquals(1, s.adapter.sessions.size)
            assertEquals(
                listOf("Go", "And more"),
                s.core.state.value[second]!!
                    .task.queue
                    .map { it.text },
            )
            val notice =
                s.core
                    .transcript(second)
                    .first()
                    .filterIsInstance<TranscriptEntry.Notice>()
                    .single()
            assertEquals("Waiting for a free slot: This computer already runs 1 agents", notice.text)

            s.core.stop(first)
            testScheduler.runCurrent()

            assertEquals(2, s.adapter.sessions.size)
            assertEquals(listOf("Go"), s.adapter.sessions[1].prompts)
            assertEquals(
                listOf("And more"),
                s.core.state.value[second]!!
                    .task.queue
                    .map { it.text },
            )
        }

    @Test
    fun waitingTasksStartInOrderAndOnlyOnTheirOwnMachine() =
        runTest {
            val remote = FakeAdapter()
            val box =
                RemoteMachine(
                    AdapterRegistry(listOf(remote), FeatureFlags()),
                    FakeWorkspace(),
                    NoProcesses,
                    "/srv/octo",
                )
            var next = 0
            val s =
                setup(
                    newId = { "id${++next}" },
                    machines = mapOf("box" to box),
                    agentLimits =
                        mapOf(
                            null to 1,
                            "box" to 1,
                        ),
                )
            val local = task(s)
            val onBox = task(s, machine = "box")
            val second = task(s)
            val third = task(s)
            val secondOnBox = task(s, machine = "box")

            s.core.stop(onBox)
            testScheduler.runCurrent()
            assertEquals(AgentStatus.Idle, s.status(secondOnBox))
            assertEquals(AgentStatus.Queued, s.status(second))

            s.core.stop(local)
            testScheduler.runCurrent()
            assertEquals(AgentStatus.Idle, s.status(second))
            assertEquals(AgentStatus.Queued, s.status(third))
        }

    @Test
    fun aStoppedWaitingTaskLeavesTheQueue() =
        runTest {
            val s = ids(mapOf(null to 1))
            val first = task(s)
            val second = task(s)
            val third = task(s)

            s.core.stop(second)
            assertEquals(AgentStatus.Stopped, s.status(second))
            s.core.stop(first)
            testScheduler.runCurrent()

            assertEquals(AgentStatus.Stopped, s.status(second))
            assertEquals(AgentStatus.Idle, s.status(third))
        }

    @Test
    fun startNowRunsAWaitingTaskOverTheLimit() =
        runTest {
            val s = ids(mapOf(null to 1))
            val first = task(s)
            val second = task(s)
            s.core.startNow(second)
            testScheduler.runCurrent()
            assertEquals(AgentStatus.Idle, s.status(second))

            // Two agents run against a limit of one, so the next task waits for both to end.
            val third = task(s)
            s.core.stop(first)
            testScheduler.runCurrent()
            assertEquals(AgentStatus.Queued, s.status(third))
            s.core.stop(second)
            testScheduler.runCurrent()
            assertEquals(AgentStatus.Idle, s.status(third))
        }

    @Test
    fun aRestartKeepsItsSlot() =
        runTest {
            val s = ids(mapOf(null to 1))
            val first = task(s)
            val second = task(s)

            s.core.restart(first)
            testScheduler.runCurrent()

            assertEquals(AgentStatus.Idle, s.status(first))
            assertEquals(AgentStatus.Queued, s.status(second))
        }

    @Test
    fun anAgentThatExitsOnItsOwnFreesItsSlot() =
        runTest {
            val s = ids(mapOf(null to 1))
            task(s)
            val second = task(s)

            s.adapter.sessions
                .single()
                .apply { emit(AgentEvent.Exited(1)) }
                .channel
                .close()
            testScheduler.runCurrent()

            assertEquals(AgentStatus.Idle, s.status(second))
        }

    @Test
    fun anAgentThatFailsToStartPassesTheSlotOn() =
        runTest {
            val s = ids(mapOf(null to 1))
            val first = task(s)
            val second = task(s)
            val third = task(s)

            s.adapter.failStart = true
            s.core.stop(first)
            testScheduler.runCurrent()

            assertEquals(AgentStatus.Failed, s.status(second))
            assertEquals(AgentStatus.Failed, s.status(third))
        }

    @Test
    fun closingStartsNoWaitingTask() =
        runTest {
            val s = ids(mapOf(null to 1))
            task(s)
            val second = task(s)

            s.core.close()
            testScheduler.runCurrent()

            assertEquals(AgentStatus.Stopped, s.status(second))
            assertEquals(1, s.adapter.sessions.size)
        }

    @Test
    fun withoutALimitNothingWaits() =
        runTest {
            val s = ids(emptyMap())
            val tasks = List(3) { task(s) }
            assertTrue(tasks.all { s.status(it) == AgentStatus.Idle })
        }
}
