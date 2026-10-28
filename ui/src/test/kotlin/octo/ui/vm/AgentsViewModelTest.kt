package octo.ui.vm

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import octo.core.agent.AgentId
import octo.core.agent.AuthState
import octo.core.agent.SessionStats
import octo.core.process.ProcessUsage
import octo.core.status.AgentStatus
import octo.core.task.TaskId
import octo.core.task.TaskView
import octo.testing.FakeCatalog
import octo.testing.FakeOctoCore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AgentsViewModelTest {
    private val core = FakeOctoCore()
    private val catalog = FakeCatalog(core)

    private fun TestScope.settle() = testScheduler.runCurrent()

    private fun view(
        id: String,
        status: AgentStatus,
        agent: String = "demo",
        machine: String? = null,
        stats: SessionStats = SessionStats(),
        options: Map<String, String> = emptyMap(),
    ) = TaskView(
        FakeOctoCore.task(TaskId(id)).copy(agent = AgentId(agent), machine = machine, options = options),
        status,
        stats = stats,
    )

    @Test
    fun listsRunningAgentsWithTheirOwnFigures() =
        runTest {
            core.put(
                view(
                    "a",
                    AgentStatus.Working,
                    stats =
                        SessionStats(
                            model = "opus",
                            contextUsed = 45_200,
                            contextWindow = 200_000,
                            inputTokens = 12_345,
                            outputTokens = 999,
                            cacheReadTokens = 1_500_000,
                        ),
                ),
            )
            core.put(view("b", AgentStatus.Stopped))
            core.put(view("c", AgentStatus.Idle, agent = "claude", machine = "box"))
            core.put(view("d", AgentStatus.Starting, agent = "missing"))
            core.usage.value = mapOf(TaskId("a") to ProcessUsage(12.34, 512L * 1024 * 1024))
            val vm = AgentsViewModel(core, catalog, backgroundScope)
            settle()

            val rows =
                vm.state.value.rows
                    .associateBy { it.id.value }
            assertEquals(setOf("a", "c", "d"), rows.keys)

            val a = rows.getValue("a")
            assertEquals("Demo", a.agent)
            assertEquals(CREDENTIAL_MASK, a.credentials)
            assertEquals("12.3%", a.cpu)
            assertEquals("512 MB", a.memory)
            assertEquals("opus", a.model)
            assertEquals("45.2k / 200k (22%)", a.context)
            assertEquals("in 12.3k · out 999 · cache read 1.5M", a.tokens)

            val remote = rows.getValue("c")
            assertEquals("Claude", remote.agent)
            assertEquals(CREDENTIAL_MASK, remote.credentials)
            assertEquals(REMOTE, remote.cpu)
            assertEquals(REMOTE, remote.memory)

            val starting = rows.getValue("d")
            assertEquals("Unknown", starting.credentials)
            assertEquals(NONE, starting.cpu)
            assertEquals(NONE, starting.model)
            assertEquals(NONE, starting.context)
            assertEquals(NONE, starting.tokens)
            vm.close()
        }

    @Test
    fun credentialsAreNeverShown() =
        runTest {
            val secret = "sk-live-0123456789"
            core.put(view("a", AgentStatus.Idle, options = mapOf("apiKey" to secret, "token" to secret)))
            val vm = AgentsViewModel(core, catalog, backgroundScope)
            settle()

            val row =
                vm.state.value.rows
                    .single()
            assertFalse(row.toString().contains(secret))
            assertEquals(
                listOf(CREDENTIAL_MASK, "Not signed in", "Unknown", "Unknown"),
                listOf(AuthState.LoggedIn, AuthState.LoggedOut, AuthState.Unknown, null).map(::credentialLabel),
            )
            vm.close()
        }

    @Test
    fun stopEndsThatAgent() =
        runTest {
            core.put(view("a", AgentStatus.Working))
            core.put(view("b", AgentStatus.Idle))
            val vm = AgentsViewModel(core, catalog, backgroundScope)
            settle()

            vm.stop(TaskId("b"))
            settle()

            assertEquals(listOf(TaskId("b")), core.stopped)
            assertFalse(
                vm.state.value.rows
                    .any { it.stopping },
            )
            vm.close()
        }

    @Test
    fun theScreenClosesWhenATaskIsPicked() =
        runTest {
            core.put(view("a", AgentStatus.Idle))
            val main = MainViewModel(core, backgroundScope)
            main.openAgents()
            assertTrue(main.state.value.agentsOpen)

            main.select(TaskId("a"))
            settle()
            assertFalse(main.state.value.agentsOpen)
        }

    @Test
    fun numbersReadShort() {
        assertEquals(listOf("950", "1k", "12.3k", "1.2M"), listOf(950L, 1_000L, 12_345L, 1_234_567L).map(::tokenCount))
        assertEquals("1.5 GB", memoryLabel(1_610_612_736L))
        assertEquals("? / 128k", contextLabel(SessionStats(contextWindow = 128_000)))
        assertEquals("3.1k", contextLabel(SessionStats(contextUsed = 3_100)))
        assertEquals("cache write 20", tokensLabel(SessionStats(cacheWriteTokens = 20)))
    }
}
