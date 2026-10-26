package octo.adapters.plugin

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import octo.core.agent.AgentEvent
import octo.core.agent.AgentId
import octo.core.agent.Role
import octo.core.agent.SessionSpec
import octo.core.plugins.PluginAgent
import octo.testing.Events
import octo.testing.FakeProcessLauncher
import octo.testing.readFixture
import octo.testing.replay
import kotlin.test.Test
import kotlin.test.assertEquals

class PluginAgentAdapterTest {
    private val agent = PluginAgent("demo-acp", "Demo ACP", "demo-acp", listOf("--acp", "--quiet"), "1.0.0")

    private fun TestScope.adapter(): Pair<PluginAgentAdapter, FakeProcessLauncher> {
        val launcher =
            FakeProcessLauncher(backgroundScope, mapOf("demo-acp" to "/usr/local/bin/demo-acp")) {
                if (spec.args == listOf("--version")) {
                    emit("demo-acp 1.0.0")
                } else {
                    replay(readFixture("src/commonTest/fixtures/turn.transcript"))
                }
            }
        return PluginAgentAdapter(launcher, agent, "Demo", StandardTestDispatcher(testScheduler)) to launcher
    }

    @Test
    fun startsTheDeclaredCommandAndRunsATurnOverAcp() =
        runTest {
            val (adapter, launcher) = adapter()
            assertEquals(AgentId("demo-acp"), adapter.id)
            assertEquals("Demo ACP (plugin Demo)", adapter.displayName)

            val session = adapter.start(SessionSpec("/repo"))
            assertEquals("/usr/local/bin/demo-acp", launcher.launched.last().command)
            assertEquals(listOf("--acp", "--quiet"), launcher.launched.last().args)
            val events = Events(backgroundScope, session)
            session.prompt("Hi")
            val all = events.drain()

            assertEquals(
                AgentEvent.Message(Role.Assistant, "Hello."),
                all.filterIsInstance<AgentEvent.Message>().single(),
            )
            assertEquals(AgentEvent.Exited(0), all.last())
        }

    @Test
    fun probesTheVersionAgainstTheManifest() =
        runTest {
            val probe = adapter().first.probe()

            assertEquals("1.0.0", probe.version)
            assertEquals("1.0.0", probe.verifiedVersion)
        }
}
