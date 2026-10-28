package octo.core.git

import kotlinx.io.files.Path
import octo.core.process.ProcessResult
import octo.core.process.ProcessSpec
import octo.core.process.run

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

    /**
     * A new worktree at [path] on a new [branch] cut from [source]'s HEAD, with [source]'s
     * uncommitted and untracked changes copied over as uncommitted changes. [source] is not touched.
     */
    suspend fun forkWorktree(
        repo: String,
        source: String,
        path: String,
        branch: String,
    )

    /** Runs [command] with `/bin/sh -c` in [worktree]; cancelling it kills the command. */
    suspend fun runSetup(
        worktree: String,
        command: String,
    ): ProcessResult

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

    /**
     * Records [worktree]'s tracked and untracked files as a commit on [ref], on top of HEAD. The
     * worktree's index, branch and stash are not touched.
     */
    suspend fun snapshot(
        worktree: String,
        ref: String,
    ): Unit = throw UnsupportedOperationException("This workspace keeps no checkpoints")

    /** Makes [worktree]'s files match the snapshot on [ref]; HEAD and the index stay as they are. */
    suspend fun restore(
        worktree: String,
        ref: String,
    ): Unit = throw UnsupportedOperationException("This workspace keeps no checkpoints")

    /** Deletes [ref] and every ref below it, such as `refs/octo/<task>`. */
    suspend fun deleteRefs(
        repo: String,
        ref: String,
    ) {
    }
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

    // The snapshot goes through a separate index, so the source's own index and files stay as they
    // are while the agent there keeps working.
    override suspend fun forkWorktree(
        repo: String,
        source: String,
        path: String,
        branch: String,
    ) {
        val head = git.run(source, listOf("rev-parse", "HEAD")).trim()
        createWorktree(repo, path, branch, head)
        if (!isDirty(source)) return
        val tree = filesTree(source, FORK_INDEX)
        // Only the files change, so the fork's index stays at HEAD and the copy shows as uncommitted.
        git.run(path, listOf("restore", "--source=$tree", "--worktree", "--", "."))
    }

    override suspend fun snapshot(
        worktree: String,
        ref: String,
    ) {
        val tree = filesTree(worktree, CHECKPOINT_INDEX)
        // A fixed identity, so a checkpoint never depends on (or signs with) the user's git config.
        val commit =
            git
                .run(
                    worktree,
                    listOf("commit-tree", "--no-gpg-sign", tree, "-p", "HEAD", "-m", "Octo checkpoint"),
                    env = IDENTITY,
                ).trim()
        git.run(worktree, listOf("update-ref", ref, commit))
    }

    // A two-tree read-tree in a separate index moves the files from what they are now to the
    // snapshot, deleting files created since, while ignored files, HEAD and the real index stay.
    override suspend fun restore(
        worktree: String,
        ref: String,
    ) {
        val target = git.run(worktree, listOf("rev-parse", "--verify", "$ref^{tree}")).trim()
        val env = mapOf("GIT_INDEX_FILE" to indexPath(worktree, CHECKPOINT_INDEX))
        val current = filesTree(worktree, CHECKPOINT_INDEX)
        git.run(worktree, listOf("read-tree", "-m", "-u", current, target), env = env)
    }

    override suspend fun deleteRefs(
        repo: String,
        ref: String,
    ) {
        val refs =
            git
                .run(repo, listOf("for-each-ref", "--format=%(refname)", ref))
                .lines()
                .filter { it.isNotBlank() }
        if (refs.isEmpty()) return
        git.run(repo, listOf("update-ref", "--stdin"), stdin = refs.joinToString("\n") { "delete $it" })
    }

    /** The tree of [dir]'s files as they are, tracked and untracked, built in the separate [index]. */
    private suspend fun filesTree(
        dir: String,
        index: String,
    ): String {
        val env = mapOf("GIT_INDEX_FILE" to indexPath(dir, index))
        git.run(dir, listOf("read-tree", "HEAD"), env = env)
        git.run(dir, listOf("add", "--all"), env = env)
        // Fresh entries carry no stat data, which read-tree -u would take as changed files.
        git.run(dir, listOf("update-index", "-q", "--refresh"), okCodes = setOf(0, 1), env = env)
        return git.run(dir, listOf("write-tree"), env = env).trim()
    }

    private suspend fun indexPath(
        dir: String,
        name: String,
    ) = git.run(dir, listOf("rev-parse", "--path-format=absolute", "--git-path", name)).trim()

    // The same launcher as git, so on a remote machine the command runs there.
    override suspend fun runSetup(
        worktree: String,
        command: String,
    ): ProcessResult = git.launcher.run(ProcessSpec("/bin/sh", listOf("-c", command), workingDir = worktree))

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

    private companion object {
        /** Reused by every fork of a worktree, so at most one is left in its git directory. */
        const val FORK_INDEX = "octo-fork-index"
        const val CHECKPOINT_INDEX = "octo-checkpoint-index"

        val IDENTITY =
            mapOf(
                "GIT_AUTHOR_NAME" to "Octo",
                "GIT_AUTHOR_EMAIL" to "octo@localhost",
                "GIT_COMMITTER_NAME" to "Octo",
                "GIT_COMMITTER_EMAIL" to "octo@localhost",
            )
    }
}
