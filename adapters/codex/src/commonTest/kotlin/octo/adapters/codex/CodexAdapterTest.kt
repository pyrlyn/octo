package octo.adapters.codex

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import octo.core.agent.AgentEvent
import octo.core.agent.AgentSessionRef
import octo.core.agent.AuthState
import octo.core.agent.Capability
import octo.core.agent.LogStream
import octo.core.agent.PermissionKind
import octo.core.agent.PermissionMode
import octo.core.agent.PlanEntry
import octo.core.agent.RequestResponse
import octo.core.agent.Role
import octo.core.agent.SessionSpec
import octo.core.agent.SessionStats
import octo.core.agent.StopReason
import octo.core.agent.ToolStatus
import octo.testing.Events
import octo.testing.FakeProcess
import octo.testing.FakeProcessLauncher
import octo.testing.readFixture
import octo.testing.replay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CodexAdapterTest {
    private val binaries = mapOf("codex" to "/bin/codex")

    private fun TestScope.adapter(script: suspend FakeProcess.() -> Unit): Pair<CodexAdapter, FakeProcessLauncher> {
        val launcher = FakeProcessLauncher(backgroundScope, binaries, script)
        return CodexAdapter(launcher, StandardTestDispatcher(testScheduler)) to launcher
    }

    private fun TestScope.fixture(name: String) = adapter { replay(readFixture("src/commonTest/fixtures/$name")) }

    @Test
    fun aModelSwitchGoesOutWithTheNextTurn() =
        runTest {
            val (adapter, _) = fixture("model.transcript")
            val session = adapter.start(SessionSpec("/repo"))
            session.setModel("gpt-5-codex")
            session.prompt("Go")
            val events = Events(backgroundScope, session).drain()
            assertEquals(StopReason.EndTurn, events.filterIsInstance<AgentEvent.TurnEnded>().single().stopReason)
        }

    @Test
    fun permissionModesMapToThreadAndTurnSettings() =
        runTest {
            val (adapter, _) = fixture("permission-modes.transcript")
            assertEquals(PermissionMode.entries.toSet(), adapter.permissionModes)
            val session = adapter.start(SessionSpec("/repo", permissionMode = PermissionMode.FullAccess))
            session.setPermissionMode(PermissionMode.AutoAcceptEdits)
            session.prompt("Go")
            val events = Events(backgroundScope, session).drain()
            assertEquals(StopReason.EndTurn, events.filterIsInstance<AgentEvent.TurnEnded>().single().stopReason)
        }

    @Test
    fun turnMapsToEvents() =
        runTest {
            val (adapter, launcher) = fixture("turn.transcript")
            val session = adapter.start(SessionSpec("/repo", options = mapOf(CodexAdapter.OPTION_MODEL to "gpt-5")))
            assertEquals(listOf("app-server"), launcher.launched.single().args)
            session.prompt("List the files")
            val events = Events(backgroundScope, session).drain()

            assertEquals(AgentEvent.SessionReady(AgentSessionRef("thr-1")), events[0])
            assertEquals(AgentEvent.StatsUpdated(SessionStats(model = "gpt-5")), events[1])
            assertEquals(AgentEvent.TurnStarted, events[2])
            assertEquals(listOf("Look", "ing."), events.filterIsInstance<AgentEvent.MessageDelta>().map { it.text })
            assertEquals(
                listOf(AgentEvent.Message(Role.Assistant, "Looking.")),
                events.filterIsInstance<AgentEvent.Message>(),
            )
            assertTrue(AgentEvent.Thought("Use ls.") in events)
            assertTrue(AgentEvent.Log(LogStream.Protocol, "not json at all") in events)

            val ls = events.filterIsInstance<AgentEvent.ToolCall>().filter { it.id == "c1" }
            assertEquals(listOf(ToolStatus.InProgress, ToolStatus.Completed), ls.map { it.status })
            assertEquals("Run: ls", ls.first().title)
            assertEquals("execute", ls.first().kind)
            assertEquals("a.txt\nb.txt", ls.last().detail)

            val edit = events.filterIsInstance<AgentEvent.ToolCall>().single { it.id == "f1" }
            assertEquals(AgentEvent.ToolCall("f1", "Edit: a.txt", "edit", ToolStatus.Failed, "-a\n+b"), edit)

            assertEquals(
                AgentEvent.PlanUpdated(listOf(PlanEntry("List", true), PlanEntry("Report", false))),
                events.filterIsInstance<AgentEvent.PlanUpdated>().single(),
            )
            val end = events.filterIsInstance<AgentEvent.TurnEnded>().single()
            assertEquals(StopReason.EndTurn, end.stopReason)
            assertEquals(120L, end.usage?.inputTokens)
            assertEquals(30L, end.usage?.outputTokens)
            assertEquals(AgentEvent.Exited(0), events.last())

            assertEquals(
                listOf(
                    SessionStats(model = "gpt-5"),
                    SessionStats(
                        contextUsed = 150,
                        contextWindow = 272_000,
                        inputTokens = 500,
                        outputTokens = 90,
                        cacheReadTokens = 400,
                    ),
                ),
                events.filterIsInstance<AgentEvent.StatsUpdated>().map { it.stats },
            )
        }

    @Test
    fun approvalsAndQuestionsRoundTrip() =
        runTest {
            val (adapter, launcher) = fixture("permission.transcript")
            val session = adapter.start(SessionSpec("/repo"))
            val events = Events(backgroundScope, session)
            session.prompt("Clean up")

            val command = events.next<AgentEvent.PermissionRequested>()
            assertEquals("0", command.id)
            assertEquals("Run: rm a.txt (Deletes a file)", command.title)
            assertEquals(
                listOf(PermissionKind.AllowOnce, PermissionKind.AllowAlways, PermissionKind.RejectOnce),
                command.options.map { it.kind },
            )
            session.respond("0", RequestResponse.Option("acceptForSession"))

            val ask = events.next<AgentEvent.QuestionAsked>()
            assertEquals("fmt", ask.questions.single().id)
            assertEquals("Which format?", ask.questions.single().text)
            assertEquals(listOf("Summary", "Detailed"), ask.questions.single().options)
            session.respond("1", RequestResponse.Answers(mapOf("fmt" to "Summary")))

            assertEquals("Apply file changes", events.next<AgentEvent.PermissionRequested>().title)
            session.respond("2", RequestResponse.Option("decline"))

            val all = events.drain()
            assertEquals(
                listOf("0", "1", "2", "3"),
                all.filterIsInstance<AgentEvent.RequestResolved>().map { it.id },
            )
            assertEquals(StopReason.EndTurn, all.filterIsInstance<AgentEvent.TurnEnded>().single().stopReason)
            assertEquals(
                9,
                launcher.processes
                    .single()
                    .written.size,
            )
        }

    @Test
    fun resumeCancelAndErrorTurns() =
        runTest {
            val (adapter, _) = fixture("cancel.transcript")
            val session = adapter.start(SessionSpec("/repo", resume = AgentSessionRef("old")))
            val events = Events(backgroundScope, session)
            assertEquals(AgentEvent.SessionReady(AgentSessionRef("old")), events.next<AgentEvent.SessionReady>())
            session.prompt("Work")
            session.cancel()
            assertEquals(StopReason.Cancelled, events.next<AgentEvent.TurnEnded>().stopReason)

            session.prompt("Again")
            val failed = events.next<AgentEvent.TurnEnded>()
            assertEquals(StopReason.Error, failed.stopReason)
            assertEquals("API Error: 529 overloaded", failed.message)
            val all = events.drain()
            assertTrue(AgentEvent.Log(LogStream.Protocol, "stream disconnected") in all)
            assertEquals(AgentEvent.Exited(1), all.last())
        }

    @Test
    fun aForkStartsANewThread() =
        runTest {
            val (adapter, _) = fixture("fork.transcript")
            val session = adapter.start(SessionSpec("/fork", resume = AgentSessionRef("old"), fork = true))
            assertEquals(
                AgentEvent.SessionReady(AgentSessionRef("thr-2")),
                Events(backgroundScope, session).next<AgentEvent.SessionReady>(),
            )
        }

    @Test
    fun steersTheRunningTurnOnly() =
        runTest {
            val (adapter, _) = fixture("steer.transcript")
            assertTrue(Capability.Steer in adapter.capabilities)
            val session = adapter.start(SessionSpec("/repo"))
            val events = Events(backgroundScope, session)
            session.prompt("Format the file")
            session.steer("Use tabs")
            assertEquals(StopReason.EndTurn, events.next<AgentEvent.TurnEnded>().stopReason)
            assertFailsWith<IllegalStateException> { session.steer("Too late") }
        }

    @Test
    fun rollbackRevertsTheThreadBeforeTheFirstDroppedTurn() =
        runTest {
            val (adapter, _) = fixture("rollback.transcript")
            val session = adapter.start(SessionSpec("/repo"))
            val events = Events(backgroundScope, session)
            session.prompt("First")
            events.next<AgentEvent.TurnEnded>()
            session.prompt("Second")
            events.next<AgentEvent.TurnEnded>()
            assertFailsWith<IllegalStateException> { session.rollback(3) }
            session.rollback(1)
            assertFailsWith<IllegalStateException>("turn-2 is gone, one turn is left") { session.rollback(2) }
        }

    @Test
    fun probeReadsVersionAndLogin() =
        runTest {
            val (adapter, _) =
                adapter {
                    when (spec.args) {
                        listOf("--version") -> emit("codex-cli 0.162.1")
                        else -> emit("Logged in using ChatGPT")
                    }
                }
            val probe = adapter.probe()
            assertEquals("0.162.1", probe.version)
            assertFalse(probe.versionDrift)
            assertEquals(AuthState.LoggedIn, probe.auth)
        }

    @Test
    fun probeReportsLoggedOut() =
        runTest {
            val (adapter, _) =
                adapter {
                    if (spec.args == listOf("--version")) {
                        emit("codex-cli 0.162.1")
                    } else {
                        emitError("Not logged in")
                        exit(1)
                    }
                }
            assertEquals(AuthState.LoggedOut, adapter.probe().auth)
        }
}
