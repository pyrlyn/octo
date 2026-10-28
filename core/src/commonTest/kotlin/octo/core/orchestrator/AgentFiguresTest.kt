package octo.core.orchestrator

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import octo.core.agent.AdapterRegistry
import octo.core.agent.AgentEvent
import octo.core.agent.AgentId
import octo.core.agent.SessionStats
import octo.core.flags.FeatureFlags
import octo.core.process.ProcessMonitor
import octo.core.process.ProcessUsage
import octo.core.task.NewTask
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class AgentFiguresTest {
    private val newTask = NewTask(repo = "/src/app", baseRef = "main", agent = AgentId("fake"), title = "Work")

    private class FakeMonitor : ProcessMonitor {
        val asked = mutableListOf<Set<Long>>()

        override suspend fun sample(pids: Set<Long>): Map<Long, ProcessUsage> {
            asked += pids
            return pids.associateWith { ProcessUsage(cpuPercent = it.toDouble(), memoryBytes = it * MIB) }
        }
    }

    @Test
    fun aMissingFieldKeepsItsLastValue() {
        val first = SessionStats(model = "m1", contextUsed = 10, inputTokens = 5)
        val next = first.merge(SessionStats(contextUsed = 20, contextWindow = 100))

        assertEquals(SessionStats(model = "m1", contextUsed = 20, contextWindow = 100, inputTokens = 5), next)
    }

    @Test
    fun statsFoldIntoTheTaskAndAreClearedWhenTheAgentRestarts() =
        runTest {
            val s = setup()
            val id = s.core.createTask(newTask)
            testScheduler.runCurrent()
            val session = s.adapter.sessions.single()

            session.emit(
                AgentEvent.StatsUpdated(SessionStats(model = "opus")),
                AgentEvent.StatsUpdated(SessionStats(contextUsed = 1_200, contextWindow = 200_000)),
                AgentEvent.StatsUpdated(SessionStats(inputTokens = 300, outputTokens = 40)),
            )
            testScheduler.runCurrent()
            assertEquals(
                SessionStats(
                    model = "opus",
                    contextUsed = 1_200,
                    contextWindow = 200_000,
                    inputTokens = 300,
                    outputTokens = 40,
                ),
                s.core.state.value[id]!!
                    .stats,
            )

            s.core.restart(id)
            testScheduler.runCurrent()
            assertEquals(
                SessionStats(),
                s.core.state.value[id]!!
                    .stats,
            )
        }

    @Test
    fun processUsageCoversRunningLocalAgentsOnly() =
        runTest {
            val remoteAdapter = FakeAdapter(pid = 42)
            val box =
                RemoteMachine(
                    AdapterRegistry(listOf(remoteAdapter), FeatureFlags()),
                    FakeWorkspace(),
                    NoProcesses,
                    "/srv/octo",
                )
            val monitor = FakeMonitor()
            var next = 0
            val s =
                setup(
                    adapter = FakeAdapter(pid = 41),
                    machines = mapOf("box" to box),
                    newId = { "id${++next}" },
                    processes = monitor,
                )
            val local = s.core.createTask(newTask)
            s.core.createTask(newTask.copy(machine = "box"))
            testScheduler.runCurrent()

            assertEquals(mapOf(local to ProcessUsage(41.0, 41 * MIB)), s.core.processUsage.first())
            assertEquals(setOf(41L), monitor.asked.single())

            s.core.stop(local)
            assertEquals(emptyMap(), s.core.processUsage.first())
        }

    @Test
    fun processUsageSamplesAgainAfterTheInterval() =
        runTest {
            val monitor = FakeMonitor()
            val s = setup(adapter = FakeAdapter(pid = 7), processes = monitor)
            s.core.createTask(newTask)
            testScheduler.runCurrent()

            val samples =
                s.core.processUsage
                    .take(3)
                    .toList()

            assertEquals(3, samples.size)
            assertEquals(3, monitor.asked.size)
        }

    @Test
    fun withoutAMonitorThereAreNoFigures() =
        runTest {
            val s = setup(adapter = FakeAdapter(pid = 7))
            s.core.createTask(newTask)
            testScheduler.runCurrent()

            assertEquals(emptyMap(), s.core.processUsage.first())
        }

    private companion object {
        const val MIB = 1024L * 1024
    }
}
