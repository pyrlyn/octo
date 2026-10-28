package octo.adapters.zcode

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import octo.core.agent.AgentEvent
import octo.core.agent.AgentSessionRef
import octo.core.agent.LogStream
import octo.core.agent.PermissionMode
import octo.core.agent.PromptImage
import octo.core.agent.Role
import octo.core.agent.SessionSpec
import octo.core.agent.SessionStats
import octo.core.agent.StopReason
import octo.core.agent.ToolStatus
import octo.core.agent.Usage
import octo.testing.Events
import octo.testing.FakeProcessLauncher
import octo.testing.readFixture
import octo.testing.replay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ZCodeAdapterTest {
    private fun TestScope.adapter(): Pair<ZCodeAdapter, FakeProcessLauncher> {
        val launcher =
            FakeProcessLauncher(backgroundScope, mapOf("zcode" to "/bin/zcode")) {
                when {
                    spec.args == listOf("--version") -> {
                        emit("3.16.2")
                    }

                    "--prompt=Hi" in spec.args -> {
                        replay(readFixture("src/commonTest/fixtures/turn.transcript"))
                    }

                    "--prompt=Again" in spec.args -> {
                        replay(readFixture("src/commonTest/fixtures/resume.transcript"))
                    }

                    "--prompt=Break" in spec.args -> {
                        replay(readFixture("src/commonTest/fixtures/failed.transcript"))
                    }

                    else -> {
                        emit(
                            """{"type":"model.streaming","sessionId":"old","payload":{"kind":"text_delta","delta":"…"}}""",
                        )
                        awaitCancellation()
                    }
                }
            }
        return ZCodeAdapter(launcher, StandardTestDispatcher(testScheduler)) to launcher
    }

    @Test
    fun runsATurnPerProcessAndResumesTheSession() =
        runTest {
            val (adapter, launcher) = adapter()
            val session = adapter.start(SessionSpec("/repo", permissionMode = PermissionMode.AutoAcceptEdits))
            val events = Events(backgroundScope, session)
            session.prompt("Hi")
            val first = events.next<AgentEvent.TurnEnded>()
            assertEquals(
                listOf(
                    "--prompt=Hi",
                    "--cwd=/repo",
                    "--mode=edit",
                    "--output-format=stream-json",
                    "--disallowed-tools=AskUserQuestion EnterPlanMode ExitPlanMode",
                    "--no-color",
                ),
                launcher.launched.single().args,
            )
            assertEquals("/repo", launcher.launched.single().workingDir)
            assertEquals(AgentEvent.TurnEnded(StopReason.EndTurn, Usage(100, 50)), first)

            session.setPermissionMode(PermissionMode.FullAccess)
            session.prompt("Again")
            events.next<AgentEvent.TurnEnded>()
            val second = launcher.launched.last().args
            assertTrue("--resume=sess_1" in second && "--mode=yolo" in second, second.toString())
            session.close()
            val all = events.drain()

            assertEquals(
                listOf(AgentEvent.SessionReady(AgentSessionRef("sess_1"))),
                all.filterIsInstance<AgentEvent.SessionReady>(),
            )
            assertFirstTurn(all)
            assertEquals(
                listOf(
                    AgentEvent.Message(Role.Assistant, "Only README.md."),
                    AgentEvent.Message(Role.Assistant, "Done."),
                ),
                all.filterIsInstance<AgentEvent.Message>(),
            )
            assertEquals(
                SessionStats(
                    contextUsed = 1600,
                    contextWindow = 200000,
                    inputTokens = 120,
                    outputTokens = 60,
                    cacheReadTokens = 40,
                    cacheWriteTokens = 0,
                ),
                all.filterIsInstance<AgentEvent.StatsUpdated>().last().stats,
            )
            assertEquals(AgentEvent.Exited(0), all.last())
        }

    private fun assertFirstTurn(all: List<AgentEvent>) {
        assertEquals(AgentEvent.Thought("Check the files."), all.filterIsInstance<AgentEvent.Thought>().single())
        val tool = all.filterIsInstance<AgentEvent.ToolCall>()
        assertEquals(
            listOf(ToolStatus.Pending, ToolStatus.InProgress, ToolStatus.Completed),
            tool.map { it.status },
        )
        assertEquals("Bash" to "execute", tool.last().title to tool.last().kind)
        assertEquals("README.md", tool.last().detail)
        assertEquals(
            listOf("Only ", "README.md."),
            all.filterIsInstance<AgentEvent.MessageDelta>().map { it.text },
        )
    }

    @Test
    fun aFailedRunEndsTheTurnWithItsError() =
        runTest {
            val (adapter, _) = adapter()
            val session = adapter.start(SessionSpec("/repo"))
            val events = Events(backgroundScope, session)
            session.prompt("Break")
            val end = events.next<AgentEvent.TurnEnded>()
            assertEquals(AgentEvent.TurnEnded(StopReason.Error, null, "No model provider configured"), end)
            session.close()
            assertTrue(AgentEvent.Log(LogStream.Stderr, "Error: No model provider configured") in events.drain())
        }

    @Test
    fun cancelKillsTheRunAndKeepsTheSession() =
        runTest {
            val (adapter, launcher) = adapter()
            val session = adapter.start(SessionSpec("/repo", resume = AgentSessionRef("old")))
            val events = Events(backgroundScope, session)
            session.prompt("Wait", listOf(PromptImage("/tmp/shot.png", "image/png", ByteArray(0))))
            events.next<AgentEvent.MessageDelta>()
            session.cancel()
            assertEquals(StopReason.Cancelled, events.next<AgentEvent.TurnEnded>().stopReason)
            val args = launcher.launched.single().args
            assertTrue(
                "--resume=old" in args && "--attach=/tmp/shot.png" in args && "--mode=build" in args,
                args.toString(),
            )
            assertTrue(launcher.processes.single().killed)
            session.close()
            val all = events.drain()
            assertEquals(AgentEvent.SessionReady(AgentSessionRef("old")), all.first())
            assertEquals(AgentEvent.Exited(0), all.last())
        }

    @Test
    fun probeReadsTheVersion() =
        runTest {
            val (adapter, _) = adapter()
            assertEquals("3.16.2", adapter.probe().version)
        }
}
