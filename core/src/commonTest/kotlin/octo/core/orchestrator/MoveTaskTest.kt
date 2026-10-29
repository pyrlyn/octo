package octo.core.orchestrator

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import octo.core.agent.AdapterRegistry
import octo.core.agent.AgentId
import octo.core.flags.FeatureFlags
import octo.core.git.GitRemote
import octo.core.task.Checkpoint
import octo.core.task.NewTask
import octo.core.task.Task
import octo.core.task.TaskId
import octo.core.task.TranscriptEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MoveTaskTest {
    /** A remote machine with a clone of `/src/app` at `/srv/app`, reached by git at `ssh://<id>`. */
    private class Box(
        val id: String,
        repos: Map<String, String> = mapOf("/src/app" to "/srv/app"),
    ) {
        val adapter = FakeAdapter()
        val workspace = FakeWorkspace().apply { root = "/srv/app" }
        val machine =
            RemoteMachine(
                AdapterRegistry(listOf(adapter), FeatureFlags()),
                workspace,
                NoProcesses,
                "/srv/octo",
                repos,
                remotes = { GitRemote("ssh://$id$it", "ssh") },
            )
    }

    private suspend fun TestScope.setup(
        vararg boxes: Box,
        checkpoints: Boolean = false,
    ): Setup {
        val s = setup(machines = boxes.associate { it.id to it.machine }, checkpoints = checkpoints)
        boxes.forEach { s.workspace.peers["ssh://${it.id}/srv/app"] = it.workspace }
        return s
    }

    private suspend fun TestScope.started(
        s: Setup,
        machine: String? = null,
    ): TaskId {
        val repo = if (machine == null) "/src/app" else "/srv/app"
        val id = s.core.createTask(NewTask(repo, "main", AgentId("fake"), "Fix it", machine = machine))
        testScheduler.runCurrent()
        return id
    }

    private fun Setup.task(id: TaskId): Task = core.state.value[id]!!.task

    private suspend fun Setup.notices(id: TaskId) =
        core
            .transcript(id)
            .first()
            .filterIsInstance<TranscriptEntry.Notice>()
            .map { it.text }

    @Test
    fun aLocalTaskMovesWithItsUncommittedWorkAndHandsTheConversationOver() =
        runTest {
            val box = Box("box")
            val s = setup(box)
            val id = started(s)
            val old = s.task(id)
            s.workspace.files[old.worktree] = "half-done fix"
            s.core.sendPrompt(id, "Fix the login bug")
            s.core.moveTask(id, "box")
            testScheduler.runCurrent()

            val task = s.task(id)
            assertEquals("box", task.machine)
            assertEquals("/srv/app", task.repo)
            assertTrue(task.worktree.startsWith("/srv/octo/"), task.worktree)
            assertEquals(old.branch, box.workspace.worktrees[task.worktree])
            assertEquals("refs/octo-move/${id.value}^", box.workspace.bases[task.worktree], "on the branch tip")
            assertEquals("half-done fix", box.workspace.files[task.worktree])
            assertEquals(listOf(old.worktree), s.workspace.removed)
            assertTrue(s.workspace.refs.isEmpty() && box.workspace.refs.isEmpty(), "no hidden refs are left")
            assertNull(
                box.adapter.specs
                    .single()
                    .resume,
                "a fresh session, not the old one",
            )
            assertEquals("Fake", task.handoffFrom)
            assertEquals(
                old.worktree,
                s.adapter.specs
                    .single()
                    .workingDir,
            )
            assertEquals(
                task.worktree,
                box.adapter.specs
                    .single()
                    .workingDir,
            )
            assertTrue("Moved to 'box'; the next prompt carries the conversation over" in s.notices(id))

            s.core.sendPrompt(id, "Go on")
            val sent =
                box.adapter.sessions
                    .single()
                    .prompts
                    .single()
            assertTrue("Fix the login bug" in sent && sent.endsWith("Go on"), "the handoff goes first")
        }

    @Test
    fun aRemoteTaskMovesBackToThisComputer() =
        runTest {
            val box = Box("box")
            val s = setup(box)
            val id = started(s, "box")
            val old = s.task(id)
            box.workspace.files[old.worktree] = "work from the box"
            s.core.moveTask(id, null)
            testScheduler.runCurrent()

            val task = s.task(id)
            assertNull(task.machine)
            assertEquals("/src/app", task.repo)
            assertEquals("work from the box", s.workspace.files[task.worktree])
            assertEquals(listOf(old.worktree), box.workspace.removed)
            assertEquals(
                task.worktree,
                s.adapter.specs
                    .single()
                    .workingDir,
            )
        }

    @Test
    fun betweenTwoRemoteMachinesTheCodeGoesThroughTheRelay() =
        runTest {
            val box = Box("box")
            val cave = Box("cave")
            val s = setup(box, cave)
            val id = started(s, "box")
            box.workspace.files[s.task(id).worktree] = "work from the box"
            s.core.moveTask(id, "cave")
            testScheduler.runCurrent()

            val task = s.task(id)
            assertEquals("cave", task.machine)
            assertEquals("work from the box", cave.workspace.files[task.worktree])
            assertEquals(listOf(Path(s.home, "relay.git").toString()), s.workspace.bares)
            assertTrue(s.workspace.refs.isEmpty(), "the relay keeps nothing")
        }

    @Test
    fun onlyMachinesWithACloneAreTargets() =
        runTest {
            val box = Box("box")
            val bare = Box("bare", repos = emptyMap())
            val s = setup(box, bare)
            val id = started(s)

            assertEquals(listOf<String?>("box"), s.core.moveTargets(id))
            val e = assertFailsWith<IllegalArgumentException> { s.core.moveTask(id, "bare") }
            assertTrue("repos" in e.message.orEmpty(), e.message)
            assertNull(s.task(id).machine)
        }

    @Test
    fun aFailedMoveLeavesTheTaskWhereItWasAndRestartsItsAgent() =
        runTest {
            val box = Box("box")
            val s = setup(box)
            val id = started(s)
            val old = s.task(id)
            s.workspace.failTransfer = true

            assertFailsWith<IllegalStateException> { s.core.moveTask(id, "box") }
            testScheduler.runCurrent()

            assertEquals(old.copy(lastStatus = s.task(id).lastStatus), s.task(id))
            assertTrue(box.workspace.worktrees.isEmpty())
            assertTrue(s.workspace.removed.isEmpty())
            assertTrue(s.workspace.refs.isEmpty())
            assertEquals(2, s.adapter.sessions.size, "the agent came back")
            assertTrue(s.notices(id).any { it.startsWith("Not moved to 'box': Connection refused") })
        }

    @Test
    fun checkpointsAreForgotten() =
        runTest {
            val box = Box("box")
            val s = setup(box, checkpoints = true)
            val id = started(s)
            s.core.sendPrompt(id, "Go")
            assertTrue(s.task(id).checkpoints.isNotEmpty())

            s.core.moveTask(id, "box")

            assertEquals(emptyList<Checkpoint>(), s.task(id).checkpoints)
            assertTrue(s.workspace.refs.isEmpty())
        }
}
