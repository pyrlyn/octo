package octo.core.publish

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import octo.core.agent.AgentId
import octo.core.git.Git
import octo.core.git.GitHubCli
import octo.core.git.GitWorkspace
import octo.core.orchestrator.UnknownTaskException
import octo.core.process.JvmProcessLauncher
import octo.core.status.AgentStatus
import octo.core.task.OctoState
import octo.core.task.Task
import octo.core.task.TaskId
import octo.core.task.TaskView
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskPublisherTest {
    private val root = Files.createTempDirectory("octo-publish").toFile().canonicalFile
    private val home = File(root, "home").apply { mkdirs() }
    private val bin = File(root, "bin").apply { mkdirs() }
    private val ghLog = File(root, "gh.log")
    private val prMarker = File(root, "pr-open")

    init {
        // A stand-in for `gh`: logs its arguments, and reports the PR as open once it was created.
        File(bin, "gh").apply {
            writeText(
                """
                |#!/bin/sh
                |printf '%s\n' "$*" >> '${ghLog.path}'
                |case "$1 $2" in
                |  "pr list") [ -f '${prMarker.path}' ] && echo "$URL"; exit 0 ;;
                |  "pr create") touch '${prMarker.path}'; echo "Creating pull request"; echo "$URL" ;;
                |  *) exit 1 ;;
                |esac
                """.trimMargin().replace("\$URL", URL),
            )
            setExecutable(true)
        }
    }

    // git and gh must never read the user's real configuration, identity or login.
    private val launcher =
        JvmProcessLauncher(
            mapOf(
                "PATH" to bin.path + File.pathSeparator + (System.getenv("PATH") ?: ""),
                "HOME" to home.path,
                "XDG_CONFIG_HOME" to File(home, ".config").path,
                "GH_CONFIG_DIR" to File(home, "gh").path,
                "GIT_CONFIG_GLOBAL" to File(home, "gitconfig").apply { writeText("") }.path,
                "GIT_CONFIG_NOSYSTEM" to "1",
                "GIT_AUTHOR_NAME" to "Octo Test",
                "GIT_AUTHOR_EMAIL" to "test@octo.invalid",
                "GIT_COMMITTER_NAME" to "Octo Test",
                "GIT_COMMITTER_EMAIL" to "test@octo.invalid",
            ),
        )
    private val git = Git(launcher)
    private val id = TaskId("t1")

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    private suspend fun setUp(): Pair<GitTaskPublisher, File> {
        val remote = File(root, "remote.git")
        git.run(root.path, listOf("init", "-q", "--bare", "-b", "main", remote.path))
        val repo = File(root, "repo").apply { mkdirs() }
        git.run(repo.path, listOf("init", "-q", "-b", "main"))
        File(repo, "a.txt").writeText("one\n")
        git.run(repo.path, listOf("add", "."))
        git.run(repo.path, listOf("commit", "-q", "-m", "init"))
        git.run(repo.path, listOf("remote", "add", "origin", remote.path))
        git.run(repo.path, listOf("push", "-q", "origin", "main"))
        val worktree = File(root, "worktrees/t1")
        GitWorkspace(git).createWorktree(repo.path, worktree.path, "octo/t1", "main")
        val task = Task(id, "Task", repo.path, worktree.path, "octo/t1", "main", AgentId("demo"))
        val state = MutableStateFlow(OctoState(tasks = listOf(TaskView(task, AgentStatus.Idle))))
        return GitTaskPublisher(state, git, GitHubCli(launcher)) to worktree
    }

    @Test
    fun commitsPushesAndOpensOnePullRequest() =
        runBlocking {
            val (publisher, worktree) = setUp()
            File(worktree, "b.txt").writeText("new\n")

            val first = publisher.publish(id, PublishRequest("Add b", "Add b", "Body text"))
            assertEquals(URL, first.pullRequestUrl)
            assertTrue(first.created)
            val commit = assertNotNull(first.commit)
            val remote = File(root, "remote.git").path
            assertEquals(commit, git.run(root.path, listOf("--git-dir", remote, "rev-parse", "octo/t1")).trim())
            assertEquals("Add b", git.run(worktree.path, listOf("log", "-1", "--format=%s")).trim())

            val again = publisher.publish(id, PublishRequest("", "Add b"))
            assertNull(again.commit, "nothing new to commit")
            assertEquals(URL, again.pullRequestUrl)
            assertEquals(false, again.created)

            val calls = ghLog.readLines()
            assertEquals(1, calls.count { it.startsWith("pr create") })
            assertTrue("pr create --base=main --head=octo/t1 --title=Add b --body=Body text" in calls, calls.toString())
        }

    @Test
    fun refusesWithoutAMessageOrForAnUnknownTask() =
        runBlocking {
            val (publisher, worktree) = setUp()
            File(worktree, "b.txt").writeText("new\n")
            assertFailsWith<IllegalArgumentException> { publisher.publish(id, PublishRequest(" ", "Title")) }
            assertFailsWith<IllegalArgumentException> { publisher.publish(id, PublishRequest("msg", "")) }
            assertFailsWith<UnknownTaskException> { publisher.publish(TaskId("nope"), PublishRequest("m", "t")) }
            assertTrue(!ghLog.exists(), "gh is not called when the request is refused")
        }

    private companion object {
        const val URL = "https://github.com/octo/demo/pull/7"
    }
}
