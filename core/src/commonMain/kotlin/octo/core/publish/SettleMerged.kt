package octo.core.publish

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import octo.core.git.GitHubCli
import octo.core.orchestrator.OctoCore
import octo.core.orchestrator.UnknownTaskException
import octo.core.task.TaskId
import octo.core.task.TaskPlace
import kotlin.coroutines.cancellation.CancellationException

/** Branches of a repository's merged pull requests. */
fun interface MergedBranches {
    suspend fun of(repo: String): Set<String>
}

/** Asks the user's own `gh`, so its login applies; only the most recent merges are looked at. */
class GitHubMergedBranches(
    private val gh: GitHubCli,
) : MergedBranches {
    override suspend fun of(repo: String): Set<String> =
        gh
            .run(
                repo,
                listOf("pr", "list", "--state=merged", "--limit=$LIMIT", "--json=headRefName", "--jq=.[].headRefName"),
            ).lines()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()

    private companion object {
        const val LIMIT = 100
    }
}

/**
 * Settles each task whose branch has a merged pull request. A task is settled once per run, so
 * one the user moves back stays where they put it.
 */
class SettleMerged(
    private val core: OctoCore,
    private val merged: MergedBranches,
) {
    private val settled = mutableSetOf<TaskId>()

    /** Looks once; a repository that cannot be asked (no `gh`, not on GitHub, offline) is skipped. */
    @Suppress("TooGenericExceptionCaught")
    suspend fun check() {
        // A remote task's repository is on another machine, out of this computer's `gh` reach.
        val waiting =
            core.state.value.tasks
                .map { it.task }
                .filter { it.machine == null && it.place != TaskPlace.Settled && it.id !in settled }
        for ((repo, tasks) in waiting.groupBy { it.repo }) {
            val branches =
                try {
                    merged.of(repo)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    continue
                }
            tasks.filter { it.branch in branches }.forEach {
                settled += it.id
                // Removed while gh was asked.
                try {
                    core.setPlace(it.id, TaskPlace.Settled)
                } catch (_: UnknownTaskException) {
                }
            }
        }
    }

    fun watch(
        scope: CoroutineScope,
        intervalMs: Long = INTERVAL_MS,
    ): Job =
        scope.launch {
            while (true) {
                check()
                delay(intervalMs)
            }
        }

    private companion object {
        const val INTERVAL_MS = 5 * 60_000L
    }
}
