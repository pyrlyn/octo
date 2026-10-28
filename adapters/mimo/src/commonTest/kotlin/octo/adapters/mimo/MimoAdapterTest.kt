package octo.adapters.mimo

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import octo.core.agent.AgentEvent
import octo.core.agent.AgentSessionRef
import octo.core.agent.Role
import octo.core.agent.SessionSpec
import octo.core.agent.SessionStats
import octo.core.agent.StopReason
import octo.core.agent.ToolStatus
import octo.testing.Events
import octo.testing.FakeProcessLauncher
import octo.testing.readFixture
import octo.testing.replay
import kotlin.test.Test
import kotlin.test.assertEquals

class MimoAdapterTest {
    private fun TestScope.adapter(): Pair<MimoAdapter, FakeProcessLauncher> {
        val launcher =
            FakeProcessLauncher(backgroundScope, mapOf("mimo" to "/bin/mimo")) {
                if (spec.args == listOf("--version")) {
                    emit("0.1.16")
                } else {
                    replay(readFixture("src/commonTest/fixtures/turn.transcript"))
                }
            }
        return MimoAdapter(launcher, StandardTestDispatcher(testScheduler)) to launcher
    }

    @Test
    fun runsATurnOverAcp() =
        runTest {
            val (adapter, launcher) = adapter()
            val session = adapter.start(SessionSpec("/repo"))
            assertEquals(listOf("acp"), launcher.launched.last().args)
            val events = Events(backgroundScope, session)
            session.prompt("List the files")
            val all = events.drain()

            assertEquals(AgentEvent.StatsUpdated(SessionStats(model = "MiMo V2.6 Pro")), all[0])
            assertEquals(AgentEvent.SessionReady(AgentSessionRef("ses_mimo1")), all[1])
            assertEquals(AgentEvent.Thought("Use ls."), all.filterIsInstance<AgentEvent.Thought>().single())
            val tool = all.filterIsInstance<AgentEvent.ToolCall>().last()
            assertEquals("ls" to ToolStatus.Completed, tool.title to tool.status)
            assertEquals(
                AgentEvent.Message(Role.Assistant, "Only README.md."),
                all.filterIsInstance<AgentEvent.Message>().single(),
            )
            assertEquals(StopReason.EndTurn, all.filterIsInstance<AgentEvent.TurnEnded>().single().stopReason)
            assertEquals(AgentEvent.Exited(0), all.last())
        }

    @Test
    fun probeReadsTheVersion() =
        runTest {
            val (adapter, _) = adapter()
            assertEquals("0.1.16", adapter.probe().version)
        }
}
