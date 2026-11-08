package octo.core.orchestrator

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import octo.core.agent.AgentEvent
import octo.core.agent.AgentId
import octo.core.agent.PermissionKind
import octo.core.agent.PermissionOption
import octo.core.agent.Role
import octo.core.agent.StopReason
import octo.core.task.NewTask
import octo.core.task.TaskId
import octo.core.task.TranscriptEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SwitchSummaryTest {
    private val newTask =
        NewTask(repo = "/src/app", baseRef = "main", agent = AgentId("fake"), title = "Fix it", prompt = "Fix the bug")

    private val fake = FakeAdapter()
    private val other = FakeAdapter(id = AgentId("other"), displayName = "Other")

    private val leaving get() = fake.sessions.single()

    private suspend fun TestScope.idle(): Pair<Setup, TaskId> {
        val s = setup(fake, others = listOf(other))
        val id = s.core.createTask(newTask)
        testScheduler.runCurrent()
        leaving.emit(
            AgentEvent.Message(Role.Assistant, "The token check is wrong."),
            AgentEvent.TurnEnded(StopReason.EndTurn),
        )
        testScheduler.runCurrent()
        return s to id
    }

    private suspend fun Setup.notices(id: TaskId) =
        core
            .transcript(id)
            .first()
            .filterIsInstance<TranscriptEntry.Notice>()
            .map { it.text }

    private suspend fun Setup.firstPromptToOther(id: TaskId): String {
        core.sendPrompt(id, "Now the tests")
        return other.sessions
            .single()
            .prompts
            .single()
    }

    @Test
    fun theLeavingAgentsSummaryGoesOutWithTheHandoff() =
        runTest {
            val (s, id) = idle()
            launch { s.core.switchAgent(id, AgentId("other"), null, summary = true) }
            testScheduler.runCurrent()
            assertTrue("short summary" in leaving.prompts.last())
            assertFalse(leaving.closed, "the agent stays until it answers")
            assertTrue(other.sessions.isEmpty())

            leaving.emit(
                AgentEvent.TurnStarted,
                AgentEvent.Message(Role.Assistant, "Fixed auth; tests are left."),
                AgentEvent.TurnEnded(StopReason.EndTurn),
            )
            testScheduler.runCurrent()
            assertTrue(leaving.closed)
            assertEquals(
                "Fixed auth; tests are left.",
                s.core.state.value[id]!!
                    .task.handoffSummary,
            )

            val handoff = s.firstPromptToOther(id)
            assertTrue("The previous agent's summary:\nFixed auth; tests are left." in handoff)
            assertTrue("Agent: The token check is wrong." in handoff)
            assertFalse("short summary" in handoff, "the summary request is not part of the conversation")
            assertNull(
                s.core.state.value[id]!!
                    .task.handoffSummary,
                "it goes out once",
            )
        }

    @Test
    fun aSilentAgentRunsOutOfTimeAndTheHandoffGoesOnWithoutIt() =
        runTest {
            val (s, id) = idle()
            launch { s.core.switchAgent(id, AgentId("other"), null, summary = true) }
            testScheduler.runCurrent()
            advanceTimeBy(120_001)
            testScheduler.runCurrent()

            assertEquals(1, leaving.cancelled)
            assertTrue(leaving.closed)
            assertTrue(
                "No summary: Fake did not answer in time; the handoff uses the transcript alone" in s.notices(id),
            )
            assertFalse("summary:" in s.firstPromptToOther(id))
        }

    @Test
    fun anAgentThatAsksForPermissionGivesNoSummary() =
        runTest {
            val (s, id) = idle()
            launch { s.core.switchAgent(id, AgentId("other"), null, summary = true) }
            testScheduler.runCurrent()
            val allow = PermissionOption("ok", "Allow", PermissionKind.AllowOnce)
            leaving.emit(AgentEvent.PermissionRequested("p1", "Run ls", listOf(allow)))
            testScheduler.runCurrent()

            assertTrue("No summary: Fake is blocked; the handoff uses the transcript alone" in s.notices(id))
            assertEquals(1, other.sessions.size)
        }

    @Test
    fun aBusyOrStoppedAgentIsNotAsked() =
        runTest {
            val (s, id) = idle()
            leaving.emit(AgentEvent.TurnStarted)
            testScheduler.runCurrent()
            s.core.switchAgent(id, AgentId("other"), null, summary = true)
            testScheduler.runCurrent()
            assertEquals(listOf("Fix the bug"), leaving.prompts)
            assertTrue("No summary: Fake is busy; the handoff uses the transcript alone" in s.notices(id))

            s.core.stop(id)
            s.core.switchAgent(id, AgentId("fake"), null, summary = true)
            testScheduler.runCurrent()
            assertTrue("No summary: Other is not running; the handoff uses the transcript alone" in s.notices(id))
        }

    @Test
    fun withoutAskingNoSummaryTurnRuns() =
        runTest {
            val (s, id) = idle()
            s.core.switchAgent(id, AgentId("other"), null)
            testScheduler.runCurrent()
            assertEquals(listOf("Fix the bug"), leaving.prompts)
            assertFalse(s.notices(id).any { "summary" in it })
        }
}
