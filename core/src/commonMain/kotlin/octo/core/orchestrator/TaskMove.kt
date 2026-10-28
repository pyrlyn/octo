package octo.core.orchestrator

import octo.core.git.GitRemote
import octo.core.git.RefTransfer
import octo.core.task.Task
import kotlin.coroutines.cancellation.CancellationException

/** Where a moved task's code now lives, and what could not be cleaned up behind it. */
internal data class Moved(
    val repo: String,
    val worktree: String,
    val leftBehind: String? = null,
)

/**
 * Moves a task's code to another machine. The worktree's files, uncommitted and untracked ones
 * included, travel as one snapshot commit on a hidden ref through git's own ssh transport, and come
 * out in a new worktree there as uncommitted changes again. The orchestrator stops and starts the
 * agent around it.
 */
internal class TaskMove(
    private val machines: Machines,
    /** A bare repository on this computer that carries the objects between two remote machines. */
    private val relay: String,
) {
    /** The machines [task] can move to: those with a clone of its repository. */
    fun targets(task: Task): List<String?> {
        val local = machines[task.machine].local(task.repo) ?: return emptyList()
        return machines.ids.filter { it != task.machine && machines[it].repo(local) != null }
    }

    /** [task]'s repository on [target]; throws when that machine has no clone of it. */
    fun repo(
        task: Task,
        target: String?,
    ): String {
        require(target != task.machine) { "The task already runs on ${where(target)}" }
        val local = machines[task.machine].local(task.repo)
        return requireNotNull(local?.let { machines[target].repo(it) }) {
            "${where(
                target,
            ).replaceFirstChar {
                it.uppercase()
            }} has no clone of ${local ?: task.repo}; add it to that machine's repos in config.json"
        }
    }

    /** Copies [task] to [repo] on [target]; nothing is left on [target] when this throws. */
    suspend fun move(
        task: Task,
        target: String?,
        repo: String,
    ): Moved {
        val from = end(task.machine, task.repo)
        val to = end(target, repo)
        val ref = "$REFS/${task.id.value}"
        try {
            from.machine.workspace.snapshot(task.worktree, ref)
            transfer(from, to, ref)
            val worktree = to.machine.worktree(repo, task.worktree.substringAfterLast('/').substringAfterLast('\\'))
            // The snapshot's parent is the branch tip, so the branch keeps its own commits only; an
            // earlier move of the task may have left the branch in that clone.
            to.machine.workspace.createWorktree(repo, worktree, task.branch, "$ref^", takeOver = true)
            restore(to, worktree, ref)
            return Moved(repo, worktree, leftBehind = removeOld(from.machine, task))
        } finally {
            forget(from, ref)
            forget(to, ref)
            if (from.remote != null && to.remote != null) forget(End(machines[null], relay, remote = null), ref)
        }
    }

    /** One side of a move; [remote] is looked up before anything changes, so cleaning up never connects. */
    private class End(
        val machine: Machine,
        val repo: String,
        val remote: GitRemote?,
    )

    private suspend fun end(
        machine: String?,
        repo: String,
    ) = machines[machine].let { End(it, repo, it.gitRemote(repo)) }

    private suspend fun transfer(
        from: End,
        to: End,
        ref: String,
    ) {
        val source = from.remote
        val target = to.remote
        when {
            source == null -> {
                from.machine.transfer.pushRefs(from.repo, checkNotNull(target), listOf(ref))
            }

            target == null -> {
                to.machine.transfer.fetchRefs(to.repo, source, listOf(ref))
            }

            // One remote machine seldom reaches the other, so the objects go through this computer.
            else -> {
                val local = machines[null].transfer
                local.initBare(relay)
                local.fetchRefs(relay, source, listOf(ref))
                local.pushRefs(relay, target, listOf(ref))
            }
        }
    }

    // A worktree that does not come out right is taken away again, so a retry starts clean.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun restore(
        to: End,
        worktree: String,
        ref: String,
    ) {
        try {
            to.machine.workspace.restore(worktree, ref)
        } catch (e: Exception) {
            if (e !is CancellationException) to.machine.workspace.removeWorktree(to.repo, worktree, force = true)
            throw e
        }
    }

    // The task already runs from its new worktree; an old one that stays is only disk space.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun removeOld(
        from: Machine,
        task: Task,
    ): String? =
        try {
            from.workspace.removeWorktree(task.repo, task.worktree, force = true)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "The old worktree ${task.worktree} was not removed: ${e.message}"
        }

    // A hidden ref left behind only takes a little space; the next move of the task replaces it.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun forget(
        end: End,
        ref: String,
    ) {
        try {
            end.machine.workspace.deleteRefs(end.repo, ref)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Nothing to do.
        }
    }

    private val Machine.transfer
        get() = workspace as? RefTransfer ?: throw UnsupportedOperationException("This machine cannot move tasks")

    private companion object {
        const val REFS = "refs/octo-move"
    }
}
