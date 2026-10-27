package octo.core.orchestrator

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import octo.core.agent.AgentId
import octo.core.agent.ToolStatus
import octo.core.process.ProcessResult
import octo.core.status.AgentStatus
import octo.core.task.NewTask
import octo.core.task.TaskId
import octo.core.task.TranscriptEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WorktreeSetupTest {
    private val newTask =
        NewTask(repo = "/src/app", baseRef = "main", agent = AgentId("fake"), title = "Fix it", prompt = "Go")
    private val setup = mapOf("/src/app/" to "npm ci")

    private suspend fun Setup.entry(id: TaskId) =
        core
            .transcript(id)
            .first()
            .filterIsInstance<TranscriptEntry.Tool>()
            .single()

    @Test
    fun theCommandRunsInTheNewWorktreeBeforeTheAgentStarts() =
        runTest {
            val s = setup(worktreeSetup = setup)
            val id = s.core.createTask(newTask)
            testScheduler.runCurrent()
            val task =
                s.core.state.value[id]!!
                    .task
            assertEquals(listOf(task.worktree to "npm ci"), s.workspace.setups)
            assertEquals(1, s.adapter.sessions.size)
            assertEquals(
                listOf("Go"),
                s.adapter.sessions
                    .single()
                    .prompts,
            )
            val entry = s.entry(id)
            assertEquals("Setup: npm ci", entry.title)
            assertEquals(ToolStatus.Completed, entry.status)
            assertEquals("installed", entry.detail)
        }

    @Test
    fun aFailedCommandFailsTheTaskAndTheAgentNeverStarts() =
        runTest {
            val s = setup(worktreeSetup = setup)
            s.workspace.setupResult = { ProcessResult(1, "", "npm ERR! missing package.json") }
            val id = s.core.createTask(newTask)
            testScheduler.runCurrent()
            val view = s.core.state.value[id]!!
            assertEquals(AgentStatus.Failed, view.status)
            assertEquals("The setup command exited with code 1; the agent was not started", view.failure)
            assertTrue(s.adapter.sessions.isEmpty())
            val entry = s.entry(id)
            assertEquals(ToolStatus.Failed, entry.status)
            assertEquals("npm ERR! missing package.json", entry.detail)
        }

    @Test
    fun aHungCommandTimesOut() =
        runTest {
            val s = setup(worktreeSetup = setup)
            s.workspace.setupResult = { awaitCancellation() }
            val id = s.core.createTask(newTask)
            testScheduler.runCurrent()
            assertEquals(
                "The setup command timed out; the agent was not started",
                s.core.state.value[id]!!
                    .failure,
            )
            assertTrue(s.adapter.sessions.isEmpty())
        }

    @Test
    fun aForkIsSetUpToo() =
        runTest {
            var next = 0
            val s = setup(newId = { "id${++next}" }, worktreeSetup = setup)
            val parent = s.core.createTask(newTask)
            testScheduler.runCurrent()
            val child = s.core.forkTask(parent)
            testScheduler.runCurrent()
            assertEquals(
                s.core.state.value[child]!!
                    .task.worktree,
                s.workspace.setups
                    .last()
                    .first,
            )
            assertEquals(2, s.adapter.sessions.size)
        }

    @Test
    fun withoutACommandNothingRuns() =
        runTest {
            val s = setup(worktreeSetup = mapOf("/src/other" to "npm ci"))
            val id = s.core.createTask(newTask)
            testScheduler.runCurrent()
            assertTrue(s.workspace.setups.isEmpty())
            assertTrue(
                s.core
                    .transcript(id)
                    .first()
                    .none { it is TranscriptEntry.Tool },
            )
            assertEquals(1, s.adapter.sessions.size)
        }
}
