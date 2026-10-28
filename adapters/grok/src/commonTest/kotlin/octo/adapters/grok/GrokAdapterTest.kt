package octo.adapters.grok

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import octo.core.agent.AgentEvent
import octo.core.agent.AgentSessionRef
import octo.core.agent.Role
import octo.core.agent.SessionSpec
import octo.core.agent.StopReason
import octo.core.agent.WrongAgentBinaryException
import octo.core.attachments.PromptImage
import octo.testing.Events
import octo.testing.FakeProcessLauncher
import octo.testing.readFixture
import octo.testing.replay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class GrokAdapterTest {
    private fun TestScope.adapter(
        version: String,
        fixture: String = "turn.transcript",
    ): Pair<GrokAdapter, FakeProcessLauncher> {
        val launcher =
            FakeProcessLauncher(backgroundScope, mapOf("grok" to "/bin/grok")) {
                if (spec.args == listOf("--version")) {
                    emit(version)
                } else {
                    replay(readFixture("src/commonTest/fixtures/$fixture"))
                }
            }
        return GrokAdapter(launcher, StandardTestDispatcher(testScheduler)) to launcher
    }

    @Test
    fun resumesAndRunsATurn() =
        runTest {
            val (adapter, launcher) = adapter("grok 1.0.67 (2fe064390d07) [stable]")
            val session = adapter.start(SessionSpec("/repo", resume = AgentSessionRef("old")))
            assertEquals(listOf("agent", "stdio"), launcher.launched.last().args)
            val events = Events(backgroundScope, session)
            session.prompt("Hi")
            val all = events.drain()

            assertEquals(AgentEvent.SessionReady(AgentSessionRef("old")), all[0])
            assertEquals(
                AgentEvent.Message(Role.Assistant, "Hello."),
                all.filterIsInstance<AgentEvent.Message>().single(),
            )
            assertEquals(StopReason.EndTurn, all.filterIsInstance<AgentEvent.TurnEnded>().single().stopReason)
            assertEquals(AgentEvent.Exited(0), all.last())
        }

    /** The fixture fails the turn when the prompt is not the one it expects. */
    private suspend fun TestScope.promptWithImage(fixture: String): StopReason {
        val (adapter, _) = adapter("grok 1.0.67 (2fe064390d07) [stable]", fixture)
        val session = adapter.start(SessionSpec("/repo", resume = AgentSessionRef("old")))
        val events = Events(backgroundScope, session)
        session.prompt("What is on it?", listOf(PromptImage("/a.png", "image/png", byteArrayOf(1, 2, 3))))
        return events
            .drain()
            .filterIsInstance<AgentEvent.TurnEnded>()
            .single()
            .stopReason
    }

    @Test
    fun anImageGoesAsImageContent() =
        runTest {
            assertEquals(
                StopReason.EndTurn,
                promptWithImage("image.transcript"),
            )
        }

    @Test
    fun anAgentWithoutImagesReadsTheirPaths() =
        runTest {
            assertEquals(
                StopReason.EndTurn,
                promptWithImage("image-paths.transcript"),
            )
        }

    @Test
    fun refusesTheCommunityCli() =
        runTest {
            val (adapter, launcher) = adapter("1.1.7")
            assertFailsWith<WrongAgentBinaryException> { adapter.start(SessionSpec("/repo")) }
            assertEquals(listOf(listOf("--version")), launcher.launched.map { it.args })
            assertNull(adapter.probe().version)
        }

    @Test
    fun probeReadsTheOfficialVersion() =
        runTest {
            val (adapter, _) = adapter("grok 1.0.67 (2fe064390d07) [stable]")
            assertEquals("1.0.67", adapter.probe().version)
        }
}
