package octo.adapters.cursor

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import octo.core.agent.AgentEvent
import octo.core.agent.AgentSessionRef
import octo.core.agent.AuthState
import octo.core.agent.LogStream
import octo.core.agent.McpServer
import octo.core.agent.PermissionKind
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CursorAdapterTest {
    private val binaries = mapOf("cursor-agent" to "/bin/cursor-agent")

    private fun TestScope.adapter(script: suspend FakeProcess.() -> Unit): Pair<CursorAdapter, FakeProcessLauncher> {
        val launcher = FakeProcessLauncher(backgroundScope, binaries, script)
        return CursorAdapter(launcher, StandardTestDispatcher(testScheduler)) to launcher
    }

    private fun TestScope.fixture(name: String) = adapter { replay(readFixture("src/commonTest/fixtures/$name")) }

    @Test
    fun turnMapsToEvents() =
        runTest {
            val (adapter, launcher) = fixture("turn.transcript")
            val session = adapter.start(SessionSpec("/repo", options = mapOf(CursorAdapter.OPTION_MODE to "plan")))
            assertEquals(listOf("acp"), launcher.launched.single().args)
            val events = Events(backgroundScope, session)
            session.prompt("List the files")
            val all = events.drain()

            assertEquals(AgentEvent.SessionReady(AgentSessionRef("s-1")), all[0])
            assertEquals(AgentEvent.TurnStarted, all[1])
            assertEquals(AgentEvent.Thought("Use ls."), all[2])
            assertEquals(listOf("Look", "ing."), all.filterIsInstance<AgentEvent.MessageDelta>().map { it.text })
            assertEquals(
                listOf(AgentEvent.Message(Role.Assistant, "Looking.")),
                all.filterIsInstance<AgentEvent.Message>(),
            )
            assertTrue(AgentEvent.Log(LogStream.Protocol, "not json at all") in all)

            val ls = all.filterIsInstance<AgentEvent.ToolCall>().filter { it.id == "t1" }
            assertEquals(listOf(ToolStatus.Pending, ToolStatus.Completed), ls.map { it.status })
            assertEquals(listOf("ls", "ls"), ls.map { it.title })
            assertEquals("execute", ls.last().kind)
            assertEquals("a.txt\nb.txt", ls.last().detail)
            assertEquals(
                AgentEvent.ToolCall("t2", "Edit a.txt", "edit", ToolStatus.Failed, "--- a.txt\n-a\n+b"),
                all.filterIsInstance<AgentEvent.ToolCall>().single { it.id == "t2" },
            )
            assertEquals(
                AgentEvent.PlanUpdated(listOf(PlanEntry("List", true), PlanEntry("Report", false))),
                all.filterIsInstance<AgentEvent.PlanUpdated>().single(),
            )
            val endIndex = all.indexOfFirst { it is AgentEvent.TurnEnded }
            assertEquals(StopReason.EndTurn, (all[endIndex] as AgentEvent.TurnEnded).stopReason)
            assertTrue(all.indexOf(AgentEvent.Message(Role.Assistant, "Looking.")) < endIndex)
            assertEquals(AgentEvent.Exited(0), all.last())
        }

    @Test
    fun modelContextAndTokenTotalsComeFromTheProtocol() =
        runTest {
            val (adapter, _) = fixture("stats.transcript")
            val session = adapter.start(SessionSpec("/repo"))
            val events = Events(backgroundScope, session)
            session.prompt("One")
            events.next<AgentEvent.TurnEnded>()
            session.prompt("Two")
            val stats = events.drain().filterIsInstance<AgentEvent.StatsUpdated>().map { it.stats }

            assertEquals(
                listOf(
                    SessionStats(model = "Composer Fast"),
                    SessionStats(contextUsed = 1200, contextWindow = 200_000),
                    SessionStats(model = "Composer Max"),
                    SessionStats(inputTokens = 100, outputTokens = 30, cacheReadTokens = 50),
                    SessionStats(inputTokens = 115, outputTokens = 35, cacheReadTokens = 50),
                ),
                stats,
            )
        }

    @Test
    fun theOlderModelsStateNamesTheModel() =
        runTest {
            val (adapter, _) = fixture("models.transcript")
            val session = adapter.start(SessionSpec("/repo"))

            assertEquals(
                SessionStats(model = "Model Two"),
                Events(backgroundScope, session).next<AgentEvent.StatsUpdated>().stats,
            )
        }

    @Test
    fun permissionsQuestionsAndPlansRoundTrip() =
        runTest {
            val (adapter, launcher) = fixture("permission.transcript")
            val session = adapter.start(SessionSpec("/repo"))
            val events = Events(backgroundScope, session)
            session.prompt("Clean up")

            val rm = events.next<AgentEvent.PermissionRequested>()
            assertEquals("100", rm.id)
            assertEquals("rm a.txt", rm.title)
            assertEquals(
                listOf(PermissionKind.AllowOnce, PermissionKind.AllowAlways, PermissionKind.RejectOnce),
                rm.options.map { it.kind },
            )
            session.respond("100", RequestResponse.Option("allow-always"))

            val ask = events.next<AgentEvent.QuestionAsked>().questions.single()
            assertEquals("Which formats?", ask.text)
            assertEquals(listOf("Summary", "Detailed", "Table"), ask.options)
            assertTrue(ask.multiSelect)
            session.respond("101", RequestResponse.Answers(mapOf("fmt" to "Summary, Table")))

            assertEquals(
                AgentEvent.PlanUpdated(listOf(PlanEntry("Delete a.txt", false), PlanEntry("Report", false))),
                events.next<AgentEvent.PlanUpdated>(),
            )
            assertEquals(
                AgentEvent.Message(Role.Assistant, "# Cleanup\n1. Delete a.txt"),
                events.next<AgentEvent.Message>(),
            )
            val plan = events.next<AgentEvent.PermissionRequested>()
            assertEquals("Plan: Cleanup", plan.title)
            session.respond("102", RequestResponse.Option(CursorSession.ACCEPT))

            assertEquals(
                AgentEvent.PlanUpdated(listOf(PlanEntry("Delete a.txt", true), PlanEntry("Report", false))),
                events.next<AgentEvent.PlanUpdated>(),
            )
            assertEquals(
                AgentEvent.ToolCall("k1", "Task: Explore", "other", ToolStatus.InProgress, "Find tests"),
                events.next<AgentEvent.ToolCall>(),
            )
            val untitled = events.next<AgentEvent.PermissionRequested>()
            assertEquals("Allow a tool call", untitled.title)
            session.respond("104", RequestResponse.Cancelled)

            val all = events.drain()
            assertEquals(
                listOf("100", "101", "102", "104"),
                all.filterIsInstance<AgentEvent.RequestResolved>().map { it.id },
            )
            assertEquals(StopReason.EndTurn, all.filterIsInstance<AgentEvent.TurnEnded>().single().stopReason)
            assertEquals(
                8,
                launcher.processes
                    .single()
                    .written.size,
            )
        }

    @Test
    fun loadDropsReplayThenCancelAndError() =
        runTest {
            val (adapter, _) = fixture("cancel.transcript")
            val session = adapter.start(SessionSpec("/repo", resume = AgentSessionRef("old")))
            val events = Events(backgroundScope, session)
            assertEquals(AgentEvent.SessionReady(AgentSessionRef("old")), events.next<AgentEvent.SessionReady>())

            session.prompt("Work")
            events.next<AgentEvent.PermissionRequested>()
            session.cancel()
            assertEquals(AgentEvent.RequestResolved("100"), events.next<AgentEvent.RequestResolved>())
            assertEquals(StopReason.Cancelled, events.next<AgentEvent.TurnEnded>().stopReason)

            session.prompt("Again")
            val failed = events.next<AgentEvent.TurnEnded>()
            assertEquals(StopReason.Error, failed.stopReason)
            assertEquals("Model overloaded", failed.message)
            val all = events.drain()
            assertFalse(all.any { it is AgentEvent.MessageDelta || it is AgentEvent.Message })
            assertEquals(AgentEvent.Exited(1), all.last())
        }

    @Test
    fun probeReadsVersionAndLogin() =
        runTest {
            val (adapter, _) =
                adapter {
                    when (spec.args) {
                        listOf("--version") -> emit("2026.09.23-86fc751")
                        else -> emit("Logged in (unable to fetch user details)")
                    }
                }
            val probe = adapter.probe()
            assertEquals("2026.09.23-86fc751", probe.version)
            assertFalse(probe.versionDrift)
            assertEquals(AuthState.LoggedIn, probe.auth)
        }

    @Test
    fun probeReportsLoggedOut() =
        runTest {
            val (adapter, _) =
                adapter {
                    if (spec.args == listOf("--version")) {
                        emit("2026.09.23-86fc751")
                    } else {
                        emit("Not logged in")
                        exit(1)
                    }
                }
            assertEquals(AuthState.LoggedOut, adapter.probe().auth)
        }

    @Test
    fun passesOctosMcpServerOnlyToAgentsThatTakeHttp() =
        runTest {
            val octo = McpServer("octo", "http://127.0.0.1:1/mcp", mapOf("Authorization" to "Bearer t"))
            for (name in listOf("mcp.transcript", "mcp-stdio-only.transcript")) {
                val (adapter, _) = fixture(name)
                val session = adapter.start(SessionSpec("/repo", mcpServers = listOf(octo)))
                val events = Events(backgroundScope, session)
                assertEquals(AgentSessionRef("s-1"), events.next<AgentEvent.SessionReady>().ref)
            }
        }
}
