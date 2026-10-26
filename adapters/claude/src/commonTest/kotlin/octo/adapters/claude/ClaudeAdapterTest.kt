package octo.adapters.claude

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import octo.core.agent.AgentEvent
import octo.core.agent.AgentSession
import octo.core.agent.AgentSessionRef
import octo.core.agent.AuthState
import octo.core.agent.LogStream
import octo.core.agent.PermissionKind
import octo.core.agent.PlanEntry
import octo.core.agent.RequestResponse
import octo.core.agent.Role
import octo.core.agent.SessionSpec
import octo.core.agent.StopReason
import octo.core.agent.ToolStatus
import octo.testing.FakeProcess
import octo.testing.FakeProcessLauncher
import octo.testing.readFixture
import octo.testing.replay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClaudeAdapterTest {
    private val binaries = mapOf("claude" to "/bin/claude")

    private fun TestScope.adapter(script: suspend FakeProcess.() -> Unit): Pair<ClaudeAdapter, FakeProcessLauncher> {
        val launcher = FakeProcessLauncher(backgroundScope, binaries, script)
        return ClaudeAdapter(launcher, StandardTestDispatcher(testScheduler)) { "new-id" } to launcher
    }

    private fun TestScope.fixture(name: String) = adapter { replay(readFixture("src/commonTest/fixtures/$name")) }

    /** Collects every event so a test can wait for one and still check the whole stream. */
    private class Events(
        scope: TestScope,
        session: AgentSession,
    ) {
        private val channel = Channel<AgentEvent>(Channel.UNLIMITED)
        val seen = mutableListOf<AgentEvent>()

        init {
            scope.backgroundScope.launch {
                session.events.collect { channel.send(it) }
                channel.close()
            }
        }

        suspend inline fun <reified T : AgentEvent> next(): T {
            while (true) {
                val event = channel.receive()
                seen += event
                if (event is T) return event
            }
        }

        suspend fun drain(): List<AgentEvent> {
            for (event in channel) seen += event
            return seen
        }
    }

    @Test
    fun newSessionArgs() =
        runTest {
            val (adapter, launcher) = fixture("turn.transcript")
            adapter.start(SessionSpec("/repo", options = mapOf(ClaudeAdapter.OPTION_MODEL to "opus")))
            val spec = launcher.launched.single()
            assertEquals("/bin/claude", spec.command)
            assertEquals("/repo", spec.workingDir)
            assertTrue(
                spec.args.containsAll(listOf("-p", "--permission-prompt-tool", "stdio", "--include-partial-messages")),
            )
            assertEquals(listOf("--model=opus", "--session-id=new-id"), spec.args.takeLast(2))
        }

    @Test
    fun resumeArgs() =
        runTest {
            val (adapter, launcher) = fixture("turn.transcript")
            val session = adapter.start(SessionSpec("/repo", resume = AgentSessionRef("old")))
            assertEquals(
                "--resume=old",
                launcher.launched
                    .single()
                    .args
                    .last(),
            )
            assertEquals(
                AgentEvent.SessionReady(AgentSessionRef("old")),
                Events(this, session).next<AgentEvent.SessionReady>(),
            )
        }

    @Test
    fun turnMapsToEvents() =
        runTest {
            val (adapter, _) = fixture("turn.transcript")
            val session = adapter.start(SessionSpec("/repo"))
            session.prompt("List the files")
            val events = Events(this, session).drain()

            assertEquals(AgentEvent.SessionReady(AgentSessionRef("new-id")), events[0])
            assertEquals(AgentEvent.TurnStarted, events[1])
            assertTrue(AgentEvent.SessionReady(AgentSessionRef("sess-1")) in events)
            assertEquals(
                listOf("Look", "ing."),
                events.filterIsInstance<AgentEvent.MessageDelta>().map { it.text },
            )
            assertTrue(AgentEvent.Thought("Use ls.") in events)
            assertTrue(AgentEvent.Message(Role.Assistant, "Looking.") in events)
            assertTrue(AgentEvent.Log(LogStream.Protocol, "not json at all") in events)

            val bash = events.filterIsInstance<AgentEvent.ToolCall>().filter { it.id == "tu1" }
            assertEquals(listOf(ToolStatus.InProgress, ToolStatus.Completed), bash.map { it.status })
            assertEquals("Bash: ls", bash.first().title)
            assertEquals("execute", bash.first().kind)
            assertEquals("a.txt\nb.txt", bash.last().detail)

            assertEquals(
                AgentEvent.PlanUpdated(listOf(PlanEntry("List", true), PlanEntry("Report", false))),
                events.filterIsInstance<AgentEvent.PlanUpdated>().single(),
            )
            val end = events.filterIsInstance<AgentEvent.TurnEnded>().single()
            assertEquals(StopReason.EndTurn, end.stopReason)
            assertEquals(120L, end.usage?.inputTokens)
            assertEquals(0.012, end.usage?.costUsd)
            assertEquals(AgentEvent.Exited(0), events.last())
        }

    @Test
    fun permissionsAndQuestionsRoundTrip() =
        runTest {
            val (adapter, launcher) = fixture("permission.transcript")
            val session = adapter.start(SessionSpec("/repo"))
            val events = Events(this, session)
            session.prompt("Clean up")

            val bash = events.next<AgentEvent.PermissionRequested>()
            assertEquals("perm-1", bash.id)
            assertEquals("Bash: rm a.txt", bash.title)
            assertEquals(
                listOf(PermissionKind.AllowOnce, PermissionKind.AllowAlways, PermissionKind.RejectOnce),
                bash.options.map { it.kind },
            )
            session.respond("perm-1", RequestResponse.Option(ClaudeSession.ALLOW_ALWAYS))

            val ask = events.next<AgentEvent.QuestionAsked>()
            assertEquals("Which format?", ask.questions.single().id)
            assertEquals(listOf("Summary", "Detailed"), ask.questions.single().options)
            session.respond("ask-1", RequestResponse.Answers(mapOf("Which format?" to "Summary")))

            val write = events.next<AgentEvent.PermissionRequested>()
            assertEquals(2, write.options.size)
            session.respond("perm-2", RequestResponse.Option(ClaudeSession.REJECT))

            val all = events.drain()
            assertEquals(
                listOf("perm-1", "ask-1", "perm-2", "perm-3"),
                all.filterIsInstance<AgentEvent.RequestResolved>().map { it.id },
            )
            assertEquals(StopReason.EndTurn, all.filterIsInstance<AgentEvent.TurnEnded>().single().stopReason)
            assertEquals(
                6,
                launcher.processes
                    .single()
                    .written.size,
            )
        }

    @Test
    fun cancelAndErrorTurns() =
        runTest {
            val (adapter, _) = fixture("cancel.transcript")
            val session = adapter.start(SessionSpec("/repo"))
            val events = Events(this, session)
            session.prompt("Work")
            session.cancel()
            assertEquals(StopReason.Cancelled, events.next<AgentEvent.TurnEnded>().stopReason)

            session.prompt("Again")
            val failed = events.next<AgentEvent.TurnEnded>()
            assertEquals(StopReason.Error, failed.stopReason)
            assertEquals("API Error: 529 overloaded", failed.message)
            assertEquals(AgentEvent.Exited(1), events.drain().last())
        }

    @Test
    fun probeReadsVersionAndLogin() =
        runTest {
            val (adapter, _) =
                adapter {
                    when (spec.args) {
                        listOf("--version") -> emit("2.1.304 (Claude Code)")
                        else -> emit("""{"loggedIn":true,"authMethod":"claude.ai"}""")
                    }
                }
            val probe = adapter.probe()
            assertEquals("2.1.304", probe.version)
            assertTrue(probe.versionDrift)
            assertEquals(AuthState.LoggedIn, probe.auth)
        }
}
