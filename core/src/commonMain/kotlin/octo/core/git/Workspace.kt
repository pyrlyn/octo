package octo.core.git

import kotlinx.io.files.Path

/** The repository operations the orchestrator needs; an interface so it can be tested without git. */
interface Workspace {
    /** The top level of the repository that contains [dir]. */
    suspend fun repoRoot(dir: String): String

    /** Local branches, then remote-tracking ones, by short name. */
    suspend fun branches(repo: String): List<String>

    /** A new worktree at [path] on a new [branch] cut from [baseRef]. */
    suspend fun createWorktree(
        repo: String,
        path: String,
        branch: String,
        baseRef: String,
    )

    /** Uncommitted or untracked changes in [worktree]. */
    suspend fun isDirty(worktree: String): Boolean

    /** Removes the worktree only; its branch is kept. */
    suspend fun removeWorktree(
        repo: String,
        worktree: String,
        force: Boolean,
    )

    suspend fun diff(
        worktree: String,
        baseRef: String,
    ): TaskDiff
}

class GitWorkspace(
    private val git: Git,
    private val dirs: Directories = LocalDirectories(),
) : Workspace {
    override suspend fun repoRoot(dir: String): String = git.run(dir, listOf("rev-parse", "--show-toplevel")).trim()

    // Refs come sorted by full name, so refs/heads lands before refs/remotes.
    override suspend fun branches(repo: String): List<String> =
        git
            .run(repo, listOf("for-each-ref", "--format=%(refname)", "refs/heads", "refs/remotes"))
            .lines()
            // A remote's HEAD is only an alias of one of its branches.
            .filter { it.isNotBlank() && !it.endsWith("/HEAD") }
            .map { it.removePrefix("refs/heads/").removePrefix("refs/remotes/") }

    override suspend fun createWorktree(
        repo: String,
        path: String,
        branch: String,
        baseRef: String,
    ) {
        Path(path).parent?.let { dirs.create(it.toString()) }
        git.run(repo, listOf("worktree", "add", "-b", branch, path, baseRef))
    }

    // A worktree that no longer exists has nothing left to lose.
    override suspend fun isDirty(worktree: String): Boolean =
        exists(worktree) && git.run(worktree, listOf("status", "--porcelain")).isNotBlank()

    private suspend fun exists(dir: String) = dirs.exists(dir)

    override suspend fun removeWorktree(
        repo: String,
        worktree: String,
        force: Boolean,
    ) {
        // A worktree deleted by hand is only stale metadata; prune clears it instead of failing.
        if (!exists(worktree)) {
            git.run(repo, listOf("worktree", "prune"))
        } else {
            git.run(repo, listOf("worktree", "remove") + (if (force) listOf("--force") else emptyList()) + worktree)
        }
    }

    override suspend fun diff(
        worktree: String,
        baseRef: String,
    ): TaskDiff {
        val mergeBase = git.run(worktree, listOf("merge-base", baseRef, "HEAD")).trim()
        val tracked = git.run(worktree, listOf("diff", "--no-color", "--no-ext-diff", "--find-renames", mergeBase))
        val untracked =
            git
                .run(worktree, listOf("ls-files", "--others", "--exclude-standard", "-z"))
                .split('\u0000')
                .filter { it.isNotEmpty() }
                .map { file ->
                    // --no-index exits with 1 when the files differ, which is always the case here.
                    git.run(
                        worktree,
                        listOf("diff", "--no-color", "--no-ext-diff", "--no-index", "--", "/dev/null", file),
                        okCodes = setOf(0, 1),
                    )
                }
        val files = DiffParser.parse(tracked) + untracked.flatMap(DiffParser::parse)
        return TaskDiff(baseRef, mergeBase, files)
    }
}
