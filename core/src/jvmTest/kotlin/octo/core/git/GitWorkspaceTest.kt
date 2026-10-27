package octo.core.git

import kotlinx.coroutines.runBlocking
import octo.core.process.JvmProcessLauncher
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitWorkspaceTest {
    private val root = Files.createTempDirectory("octo-git").toFile().canonicalFile
    private val home = File(root, "home").apply { mkdirs() }

    // git must never read the user's real configuration or identity.
    private val launcher =
        JvmProcessLauncher(
            mapOf(
                "PATH" to (System.getenv("PATH") ?: ""),
                "HOME" to home.path,
                "XDG_CONFIG_HOME" to File(home, ".config").path,
                "GIT_CONFIG_GLOBAL" to File(home, "gitconfig").apply { writeText("") }.path,
                "GIT_CONFIG_NOSYSTEM" to "1",
                "GIT_AUTHOR_NAME" to "Octo Test",
                "GIT_AUTHOR_EMAIL" to "test@octo.invalid",
                "GIT_COMMITTER_NAME" to "Octo Test",
                "GIT_COMMITTER_EMAIL" to "test@octo.invalid",
            ),
        )
    private val git = Git(launcher)
    private val workspace = GitWorkspace(git)

    private suspend fun repo(): File {
        val repo = File(root, "repo").apply { mkdirs() }
        git.run(repo.path, listOf("init", "-q", "-b", "main"))
        File(repo, "a.txt").writeText("one\ntwo\n")
        File(repo, "sub").mkdirs()
        git.run(repo.path, listOf("add", "."))
        git.run(repo.path, listOf("commit", "-q", "-m", "init"))
        return repo
    }

    @Test
    fun worktreeLifecycleAndDiff() =
        runBlocking {
            val repo = repo()
            assertEquals(repo.path, workspace.repoRoot(repo.path))

            val worktree = File(root, "worktrees/repo/task-1")
            workspace.createWorktree(repo.path, worktree.path, "octo/task-1", "main")
            assertEquals("octo/task-1", git.run(worktree.path, listOf("branch", "--show-current")).trim())
            assertFalse(workspace.isDirty(worktree.path))

            File(worktree, "b.txt").writeText("committed\n")
            git.run(worktree.path, listOf("add", "b.txt"))
            git.run(worktree.path, listOf("commit", "-q", "-m", "b"))
            File(worktree, "a.txt").writeText("one\n2\n")
            File(worktree, "new file.txt").writeText("fresh\n")
            assertTrue(workspace.isDirty(worktree.path))

            val diff = workspace.diff(worktree.path, "main")
            assertEquals(git.run(repo.path, listOf("rev-parse", "main")).trim(), diff.mergeBase)
            val byPath = diff.files.associateBy { it.path }
            assertEquals(setOf("a.txt", "b.txt", "new file.txt"), byPath.keys)
            assertEquals(FileChange.Modified, byPath.getValue("a.txt").change)
            assertEquals(1 to 1, byPath.getValue("a.txt").let { it.additions to it.deletions })
            assertEquals(FileChange.Added, byPath.getValue("b.txt").change)
            assertEquals(FileChange.Added, byPath.getValue("new file.txt").change)
            assertEquals(
                listOf(DiffLine(LineKind.Added, "fresh")),
                byPath
                    .getValue("new file.txt")
                    .hunks
                    .single()
                    .lines,
            )

            assertFailsWith<GitException> { workspace.removeWorktree(repo.path, worktree.path, force = false) }
            workspace.removeWorktree(repo.path, worktree.path, force = true)
            assertFalse(worktree.exists())
            assertEquals(
                "octo/task-1",
                git.run(repo.path, listOf("branch", "--list", "--format=%(refname:short)", "octo/task-1")).trim(),
            )
        }

    @Test
    fun aForkStartsFromTheWorktreeAsItIsAndLeavesItAlone() =
        runBlocking {
            val repo = repo()
            val source = File(root, "worktrees/repo/task-1")
            workspace.createWorktree(repo.path, source.path, "octo/task-1", "main")
            File(source, "gone.txt").writeText("bye\n")
            git.run(source.path, listOf("add", "gone.txt"))
            git.run(source.path, listOf("commit", "-q", "-m", "gone"))
            File(source, "a.txt").writeText("one\n2\n")
            File(source, "gone.txt").delete()
            File(source, "staged.txt").writeText("staged\n")
            git.run(source.path, listOf("add", "staged.txt"))
            File(source, "new file.txt").writeText("fresh\n")
            val status = git.run(source.path, listOf("status", "--porcelain"))

            val fork = File(root, "worktrees/repo/task-1-fork")
            workspace.forkWorktree(repo.path, source.path, fork.path, "octo/task-1-fork")

            assertEquals(status, git.run(source.path, listOf("status", "--porcelain")), "the source is untouched")
            assertEquals("octo/task-1-fork", git.run(fork.path, listOf("branch", "--show-current")).trim())
            assertEquals(
                git.run(source.path, listOf("rev-parse", "HEAD")),
                git.run(fork.path, listOf("rev-parse", "HEAD")),
            )
            assertEquals("one\n2\n", File(fork, "a.txt").readText())
            assertFalse(File(fork, "gone.txt").exists())
            assertEquals("staged\n", File(fork, "staged.txt").readText())
            assertEquals("fresh\n", File(fork, "new file.txt").readText())
            assertTrue(workspace.isDirty(fork.path), "the copy shows as uncommitted")

            val clean = File(root, "worktrees/repo/clean")
            workspace.forkWorktree(repo.path, repo.path, clean.path, "octo/clean")
            assertFalse(workspace.isDirty(clean.path))
        }

    @Test
    fun branchesListLocalThenRemoteWithoutTheRemoteHead() =
        runBlocking {
            val repo = repo()
            git.run(repo.path, listOf("branch", "feature"))
            git.run(repo.path, listOf("update-ref", "refs/remotes/origin/main", "HEAD"))
            git.run(repo.path, listOf("symbolic-ref", "refs/remotes/origin/HEAD", "refs/remotes/origin/main"))
            assertEquals(listOf("feature", "main", "origin/main"), workspace.branches(repo.path))
        }

    @Test
    fun removingAWorktreeDeletedByHandOnlyPrunes() =
        runBlocking {
            val repo = repo()
            val worktree = File(root, "worktrees/repo/gone")
            workspace.createWorktree(repo.path, worktree.path, "octo/gone", "main")
            worktree.deleteRecursively()
            assertFalse(workspace.isDirty(worktree.path))
            workspace.removeWorktree(repo.path, worktree.path, force = false)
            assertFalse(git.run(repo.path, listOf("worktree", "list")).contains(worktree.path))
        }
}
