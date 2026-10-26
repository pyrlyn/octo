package octo.core.publish

import kotlinx.coroutines.flow.StateFlow
import octo.core.git.Git
import octo.core.git.GitHubCli
import octo.core.orchestrator.UnknownTaskException
import octo.core.task.OctoState
import octo.core.task.TaskId

data class PublishRequest(
    val commitMessage: String,
    val title: String,
    val body: String = "",
)

data class PublishResult(
    /** The new commit, or null when the worktree had nothing to commit. */
    val commit: String?,
    val pullRequestUrl: String,
    /** False when an open pull request for the branch already existed. */
    val created: Boolean,
)

/** Commit, push and pull request in one action; only present when the `git-publish` flag is on. */
interface TaskPublisher {
    suspend fun publish(
        taskId: TaskId,
        request: PublishRequest,
    ): PublishResult
}

/**
 * Publishes through the user's own `git` and `gh`, so their credentials, hooks and signing
 * apply unchanged.
 */
class GitTaskPublisher(
    private val state: StateFlow<OctoState>,
    private val git: Git,
    private val gh: GitHubCli,
) : TaskPublisher {
    override suspend fun publish(
        taskId: TaskId,
        request: PublishRequest,
    ): PublishResult {
        val task = state.value[taskId]?.task ?: throw UnknownTaskException(taskId)
        require(request.title.isNotBlank()) { "A pull request needs a title" }
        val dir = task.worktree
        val commit = commitAll(dir, request.commitMessage)
        git.run(dir, listOf("push", "-u", "origin", task.branch))
        // `--flag=value` keeps a user-typed value that starts with "-" from being read as an option.
        val open =
            gh.run(
                dir,
                listOf("pr", "list", "--head=${task.branch}", "--state=open", "--json=url", "--jq=.[0].url // empty"),
            )
        open.lastLine()?.let { return PublishResult(commit, it, created = false) }
        val create =
            listOf(
                "pr",
                "create",
                "--base=${task.baseRef}",
                "--head=${task.branch}",
                "--title=${request.title}",
                "--body=${request.body}",
            )
        val created = gh.run(dir, create)
        val url = created.lastLine() ?: error("gh pr create printed no pull request URL")
        return PublishResult(commit, url, created = true)
    }

    private suspend fun commitAll(
        dir: String,
        message: String,
    ): String? {
        if (git.run(dir, listOf("status", "--porcelain")).isBlank()) return null
        require(message.isNotBlank()) { "The worktree has changes, so a commit message is needed" }
        git.run(dir, listOf("add", "-A"))
        git.run(dir, listOf("commit", "-q", "--message=$message"))
        return git.run(dir, listOf("rev-parse", "HEAD")).trim()
    }

    // The UI opens this URL on click, so anything but a web link from gh's output is ignored.
    private fun String.lastLine(): String? =
        lines()
            .lastOrNull {
                it.isNotBlank()
            }?.trim()
            ?.takeIf { it.startsWith("https://") }
}
