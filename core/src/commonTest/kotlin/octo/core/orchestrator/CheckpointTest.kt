package octo.core.orchestrator

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import octo.core.agent.AgentEvent
import octo.core.agent.AgentId
import octo.core.agent.Role
import octo.core.agent.StopReason
import octo.core.task.NewTask
import octo.core.task.TaskId
import octo.core.task.TranscriptEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CheckpointTest {
    private suspend fun TestScope.task(s: Setup): Pair<TaskId, String> {
        val id = s.core.createTask(NewTask("/src/app", "main", AgentId("fake"), "Fix"))
        testScheduler.runCurrent()
        return id to
            s.core.state.value[id]!!
                .task.worktree
    }

    /** Sends [prompt], lets the agent leave [files] behind and ends the turn. */
    private suspend fun TestScope.turn(
        s: Setup,
        id: TaskId,
        worktree: String,
        prompt: String,
        files: String,
    ) {
        s.core.sendPrompt(id, prompt)
        s.workspace.files[worktree] = files
        s.adapter.sessions
            .last()
            .emit(AgentEvent.TurnEnded(StopReason.EndTurn))
        testScheduler.runCurrent()
    }

    private fun Setup.checkpoints(id: TaskId) =
        core.state.value[id]!!
            .task.checkpoints

    @Test
    fun aRevertBringsTheFilesBackAndRollsTheAgentBack() =
        runTest {
            val s = setup(checkpoints = true)
            val (id, worktree) = task(s)
            s.workspace.files[worktree] = "v0"
            turn(s, id, worktree, "Go", "v1")
            turn(s, id, worktree, "More", "v2")

            val refs = s.checkpoints(id).map { it.ref }
            assertEquals(listOf("refs/octo/id1/0", "refs/octo/id1/1", "refs/octo/id1/2"), refs)
            assertEquals(mapOf(refs[0] to "v0", refs[1] to "v1", refs[2] to "v2"), s.workspace.refs)
            assertEquals(
                listOf(
                    TranscriptEntry.Checkpoint(0, refs[0]),
                    TranscriptEntry.Text(Role.User, "Go"),
                    TranscriptEntry.Checkpoint(1, refs[1]),
                    TranscriptEntry.Text(Role.User, "More"),
                    TranscriptEntry.Checkpoint(2, refs[2]),
                ),
                s.core.transcript(id).first(),
            )

            s.core.revert(id, refs[0])
            val session = s.adapter.sessions.single()
            assertEquals("v0", s.workspace.files[worktree])
            assertEquals(listOf(2), session.rollbacks)
            assertEquals(listOf(refs[0]), s.checkpoints(id).map { it.ref })
            assertEquals(setOf(refs[0]), s.workspace.refs.keys, "later refs are deleted")
            assertEquals(
                TranscriptEntry.Notice("Reverted the files to checkpoint 0 and the agent's conversation"),
                s.core
                    .transcript(id)
                    .first()
                    .last(),
            )

            turn(s, id, worktree, "Again", "v3")
            assertEquals("Again", session.prompts.last(), "nothing to tell an agent that rolled back")
            assertEquals(listOf(0, 1), s.checkpoints(id).map { it.id })
        }

    @Test
    fun anAgentThatCannotRollBackIsToldWithItsNextPrompt() =
        runTest {
            val s = setup(checkpoints = true)
            val (id, worktree) = task(s)
            turn(s, id, worktree, "First", "v1")
            turn(s, id, worktree, "Second", "v2")
            val session = s.adapter.sessions.single()
            session.refuseRollback = true

            s.core.revert(id, s.checkpoints(id)[1].ref)
            assertEquals("v1", s.workspace.files[worktree])
            assertTrue(s.checkpoints(id).all { it.run == null }, "no later revert may roll the agent back")

            turn(s, id, worktree, "Third", "v3")
            val sent = session.prompts.last()
            assertTrue(sent.startsWith("Note from Octo: the user reverted"), sent)
            assertTrue("before your turn on \"Second\"" in sent, sent)
            assertTrue(sent.endsWith("\n\nThird"), sent)
            assertNull(
                s.core.state.value[id]!!
                    .task.revertNote,
                "the note goes out once",
            )
        }

    @Test
    fun turnsOfAnEarlierSessionAreNeverRolledBack() =
        runTest {
            val s = setup(checkpoints = true)
            val (id, worktree) = task(s)
            turn(s, id, worktree, "First", "v1")
            s.core.restart(id)
            testScheduler.runCurrent()
            turn(s, id, worktree, "Second", "v2")

            s.core.revert(id, s.checkpoints(id)[1].ref)
            assertEquals(
                listOf(1),
                s.adapter.sessions
                    .last()
                    .rollbacks,
                "the live session's own turn",
            )

            s.core.revert(id, s.checkpoints(id)[0].ref)
            assertEquals(
                listOf(1),
                s.adapter.sessions
                    .last()
                    .rollbacks,
            )
            assertTrue(
                s.core.state.value[id]!!
                    .task.revertNote != null,
            )
        }

    @Test
    fun revertWaitsForTheTurnAndRemovalDropsTheRefs() =
        runTest {
            val s = setup(checkpoints = true)
            val (id, worktree) = task(s)
            turn(s, id, worktree, "First", "v1")
            s.core.sendPrompt(id, "Second")
            assertFailsWith<IllegalStateException> { s.core.revert(id, s.checkpoints(id)[0].ref) }

            s.workspace.failSnapshot = true
            s.adapter.sessions
                .single()
                .emit(AgentEvent.TurnEnded(StopReason.EndTurn))
            testScheduler.runCurrent()
            assertEquals(2, s.checkpoints(id).size, "a failed snapshot is skipped")
            assertFailsWith<IllegalArgumentException> { s.core.revert(id, "refs/octo/id1/9") }
            assertEquals(
                TranscriptEntry.Notice("Could not record a checkpoint: disk full", error = true),
                s.core
                    .transcript(id)
                    .first()
                    .last(),
            )

            s.core.removeTask(id, deleteWorktree = false)
            assertEquals(emptyMap(), s.workspace.refs)
        }

    @Test
    fun nothingIsRecordedWithTheFlagOff() =
        runTest {
            val s = setup()
            val (id, worktree) = task(s)
            turn(s, id, worktree, "First", "v1")
            assertEquals(emptyList(), s.checkpoints(id))
            assertEquals(emptyMap(), s.workspace.refs)
        }
}
