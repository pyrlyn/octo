package octo.core.orchestrator

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import octo.core.agent.AgentEvent
import octo.core.agent.AgentId
import octo.core.agent.AgentSessionRef
import octo.core.agent.Capability
import octo.core.agent.PermissionMode
import octo.core.agent.Role
import octo.core.agent.StopReason
import octo.core.status.AgentStatus
import octo.core.task.NewTask
import octo.core.task.TaskId
import octo.core.task.TranscriptEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ForkTaskTest {
    private val newTask =
        NewTask(repo = "/src/app", baseRef = "main", agent = AgentId("fake"), title = "Fix it", prompt = "Go")

    private suspend fun TestScope.forked(adapter: FakeAdapter): Triple<Setup, TaskId, TaskId> {
        var next = 0
        val s = setup(adapter, newId = { "id${++next}" })
        val parent = s.core.createTask(newTask)
        testScheduler.runCurrent()
        s.adapter.sessions
            .single()
            .emit(AgentEvent.Message(Role.Assistant, "Tried plan A."), AgentEvent.TurnEnded(StopReason.EndTurn))
        testScheduler.runCurrent()
        s.core.setPermissionMode(parent, PermissionMode.Auto)
        val child = s.core.forkTask(parent)
        testScheduler.runCurrent()
        return Triple(s, parent, child)
    }

    @Test
    fun aForkIsANewTaskOnANewBranchFromTheParentsWorktree() =
        runTest {
            val (s, parent, child) = forked(FakeAdapter(setOf(Capability.Resume, Capability.Fork)))
            val from =
                s.core.state.value[parent]!!
                    .task
            val task =
                s.core.state.value[child]!!
                    .task
            assertEquals("Fix it (fork)", task.title)
            assertEquals(parent, task.parentId)
            assertEquals("octo/fix-it-fork-id2", task.branch)
            assertEquals(listOf(from.worktree to task.worktree), s.workspace.forks)
            assertEquals(from.baseRef, task.baseRef)
            assertEquals(PermissionMode.Auto, task.permissionMode)
            assertEquals(
                AgentStatus.Idle,
                s.core.state.value[parent]!!
                    .status,
                "the parent keeps running",
            )
            val transcript = s.core.transcript(child).first()
            assertTrue(TranscriptEntry.Text(Role.Assistant, "Tried plan A.") in transcript)
            assertTrue(transcript.any { it is TranscriptEntry.Notice && "Forked from \"Fix it\"" in it.text })
        }

    @Test
    fun anAgentThatForksItsOwnSessionGetsNoHandoff() =
        runTest {
            val (s, _, child) = forked(FakeAdapter(setOf(Capability.Resume, Capability.Fork)))
            val spec = s.adapter.specs.last()
            assertEquals(AgentSessionRef("s1"), spec.resume)
            assertTrue(spec.fork)
            val task =
                s.core.state.value[child]!!
                    .task
            assertEquals(AgentSessionRef("s2"), task.sessionRef)
            assertNull(task.handoffFrom)

            s.core.sendPrompt(child, "Try plan B")
            assertEquals(
                listOf("Try plan B"),
                s.adapter.sessions
                    .last()
                    .prompts,
            )
        }

    @Test
    fun anAgentWithoutAForkGetsTheHandoff() =
        runTest {
            val (s, _, child) = forked(FakeAdapter())
            val spec = s.adapter.specs.last()
            assertNull(spec.resume)
            assertTrue(!spec.fork)
            assertEquals(
                "Fake",
                s.core.state.value[child]!!
                    .task.handoffFrom,
            )

            s.core.sendPrompt(child, "Try plan B")
            val handoff =
                s.adapter.sessions
                    .last()
                    .prompts
                    .single()
            assertTrue("Original request:\nGo" in handoff)
            assertTrue("Tried plan A." in handoff)
            assertTrue(handoff.endsWith("Try plan B"))
        }
}
