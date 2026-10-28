package octo.core.orchestrator

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import octo.core.agent.AgentSession
import octo.core.agent.Role
import octo.core.git.Workspace
import octo.core.status.AgentStatus
import octo.core.task.Checkpoint
import octo.core.task.OctoState
import octo.core.task.Task
import octo.core.task.TaskId
import octo.core.task.TaskView
import octo.core.task.Transcript
import octo.core.task.TranscriptEntry
import kotlin.coroutines.cancellation.CancellationException

/**
 * A task's checkpoints: recorded around turns while [enabled], reverted to on request, and
 * deleted with the task. The orchestrator holds the task lock and knows the agent; the git work is
 * the workspace's.
 */
internal class Checkpoints(
    private val enabled: Boolean,
    private val state: MutableStateFlow<OctoState>,
    private val transcripts: MutableStateFlow<Map<TaskId, List<TranscriptEntry>>>,
    private val workspace: (Task) -> Workspace,
) {
    // A snapshot that fails costs that checkpoint only; the agent's turn goes on.
    @Suppress("TooGenericExceptionCaught")
    suspend fun record(
        id: TaskId,
        run: String,
    ) {
        val task = task(id)?.takeIf { enabled } ?: return
        val next = (task.checkpoints.maxOfOrNull { it.id } ?: -1) + 1
        val checkpoint = Checkpoint(next, "${root(id)}/$next", run)
        try {
            workspace(task).snapshot(task.worktree, checkpoint.ref)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            note(id, TranscriptEntry.Notice("Could not record a checkpoint: ${e.message}", error = true))
            return
        }
        val all = task.checkpoints + checkpoint
        update(id) { it.copy(checkpoints = all.takeLast(MAX)) }
        note(id, TranscriptEntry.Checkpoint(checkpoint.id, checkpoint.ref))
        all.dropLast(MAX).forEach { forget(task, it.ref) }
    }

    /**
     * Restores the files of [ref] and drops the later checkpoints. [session], the agent's live
     * session of [run], is asked to drop its turns since, but only when all of them ran in it;
     * otherwise, or when it cannot, the next prompt carries a note.
     */
    suspend fun revert(
        view: TaskView,
        ref: String,
        run: String?,
        session: AgentSession?,
    ) {
        check(view.status != AgentStatus.Working && view.status != AgentStatus.Blocked) {
            "Wait for the turn to end, or cancel it, before reverting"
        }
        val task = view.task
        val id = task.id
        val target = requireNotNull(task.checkpoints.firstOrNull { it.ref == ref }) { "That checkpoint is gone" }
        workspace(task).restore(task.worktree, target.ref)
        val later = task.checkpoints.filter { it.id > target.id }
        val turns = later.size.takeIf { run != null && later.all { it.run == run } }
        val rolledBack = turns == 0 || (turns != null && session != null && rollback(session, turns))
        val note = if (rolledBack) task.revertNote else note(transcripts.value[id].orEmpty(), target)
        update(id) {
            val earlier = it.checkpoints.filter { c -> c.id <= target.id }
            // The agent still remembers the dropped turns, so no later revert may roll it back past them.
            it.copy(
                checkpoints =
                    if (rolledBack) {
                        earlier
                    } else {
                        earlier.map { c ->
                            c.copy(run = null)
                        }
                    },
                revertNote = note,
            )
        }
        val how =
            when {
                !rolledBack -> "; the next prompt tells the agent"
                turns == 0 -> ""
                else -> " and the agent's conversation"
            }
        note(id, TranscriptEntry.Notice("Reverted the files to checkpoint ${target.id}$how"))
        later.forEach { forget(task, it.ref) }
    }

    // Whatever stops the agent from rolling back, the note in the next prompt covers it.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun rollback(
        session: AgentSession,
        turns: Int,
    ): Boolean =
        try {
            session.rollback(turns)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }

    /** Deletes every checkpoint ref of [task]. */
    suspend fun forget(task: Task) {
        if (task.checkpoints.isNotEmpty()) forget(task, root(task.id))
    }

    // A ref left behind only takes a little space in the repository, never the task down.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun forget(
        task: Task,
        ref: String,
    ) {
        try {
            workspace(task).deleteRefs(task.repo, ref)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Nothing to do: removing the task tries the whole root again.
        }
    }

    private fun task(id: TaskId) = state.value[id]?.task

    private fun update(
        id: TaskId,
        change: (Task) -> Task,
    ) = state.update { s ->
        s.copy(
            tasks =
                s.tasks.map {
                    if (it.task.id ==
                        id
                    ) {
                        it.copy(task = change(it.task))
                    } else {
                        it
                    }
                },
        )
    }

    private fun note(
        id: TaskId,
        entry: TranscriptEntry,
    ) = transcripts.update { it + (id to Transcript.add(it[id].orEmpty(), entry)) }

    companion object {
        /** Older ones are dropped, refs included, so a long task does not pile up snapshots. */
        const val MAX = 100
        private const val QUOTE_LENGTH = 200

        /** Every checkpoint ref of the task sits below this one. */
        fun root(id: TaskId) = "refs/octo/${id.value}"

        /**
         * What an agent whose conversation was not rolled back reads before its next prompt,
         * naming the first message whose turn the files went back past.
         */
        fun note(
            entries: List<TranscriptEntry>,
            target: Checkpoint,
        ): String {
            val at = entries.indexOfFirst { it is TranscriptEntry.Checkpoint && it.ref == target.ref }
            val message =
                entries
                    .takeIf { at >= 0 }
                    ?.drop(at + 1)
                    ?.filterIsInstance<TranscriptEntry.Text>()
                    ?.firstOrNull { it.role == Role.User }
                    ?.text
                    ?.take(QUOTE_LENGTH)
            val point = message?.let { "before your turn on \"$it\"" } ?: "at an earlier point of this conversation"
            return "Note from Octo: the user reverted this worktree's files to how they were $point. " +
                "Changes made after that are gone, so look at the files again before relying on anything said since."
        }
    }
}
