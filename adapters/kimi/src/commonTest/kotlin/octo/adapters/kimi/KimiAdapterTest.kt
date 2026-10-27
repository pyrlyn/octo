package octo.adapters.kimi

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import octo.core.agent.AgentEvent
import octo.core.agent.AgentSessionRef
import octo.core.agent.Role
import octo.core.agent.SessionSpec
import octo.core.agent.StopReason
import octo.core.agent.WrongAgentBinaryException
import octo.testing.Events
import octo.testing.FakeProcessLauncher
import octo.testing.readFixture
import octo.testing.replay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class KimiAdapterTest {
    private fun TestScope.adapter(version: String): Pair<KimiAdapter, FakeProcessLauncher> {
        val launcher =
            FakeProcessLauncher(backgroundScope, mapOf("kimi" to "/bin/kimi")) {
                if (spec.args == listOf("--version")) {
                    emit(version)
                } else {
                    replay(readFixture("src/commonTest/fixtures/turn.transcript"))
                }
            }
        return KimiAdapter(launcher, StandardTestDispatcher(testScheduler)) to launcher
    }

    @Test
    fun logsInAgainAndRunsATurn() =
        runTest {
            val (adapter, launcher) = adapter("0.39.1")
            val session = adapter.start(SessionSpec("/repo"))
            assertEquals(listOf("acp"), launcher.launched.last().args)
            val events = Events(backgroundScope, session)
            session.prompt("Hi")
            val all = events.drain()

            assertEquals(AgentEvent.SessionReady(AgentSessionRef("kimi-1")), all[0])
            assertEquals(
                AgentEvent.Message(Role.Assistant, "Hello."),
                all.filterIsInstance<AgentEvent.Message>().single(),
            )
            assertEquals(StopReason.EndTurn, all.filterIsInstance<AgentEvent.TurnEnded>().single().stopReason)
            assertEquals(AgentEvent.Exited(0), all.last())
        }

    @Test
    fun refusesTheLegacyPythonCli() =
        runTest {
            val (adapter, launcher) = adapter("kimi, version 1.52.0")
            assertFailsWith<WrongAgentBinaryException> { adapter.start(SessionSpec("/repo")) }
            assertEquals(listOf(listOf("--version")), launcher.launched.map { it.args })
            assertNull(adapter.probe().version)
        }

    @Test
    fun probeReadsTheKimiCodeVersion() =
        runTest {
            val (adapter, _) = adapter("2.3.3")
            assertEquals("2.3.3", adapter.probe().version)
        }
}
