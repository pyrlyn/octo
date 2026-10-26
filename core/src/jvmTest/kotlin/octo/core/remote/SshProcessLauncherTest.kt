package octo.core.remote

import kotlinx.coroutines.runBlocking
import octo.core.git.Git
import octo.core.git.GitWorkspace
import octo.core.git.ShellDirectories
import octo.core.process.JvmProcessLauncher
import octo.core.process.ProcessSpec
import octo.core.process.run
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SshProcessLauncherTest {
    private val root = Files.createTempDirectory("octo-ssh").toFile().canonicalFile
    private val home = File(root, "home").apply { mkdirs() }
    private val bin = File(root, "bin").apply { mkdirs() }
    private val sshLog = File(root, "ssh.log")
    private val down = File(root, "down")

    init {
        // A stand-in for `ssh`: logs its options and host, then runs the remote command with a local
        // POSIX shell, the way sshd hands it to the login shell.
        File(bin, "ssh").apply {
            writeText(
                """
                |#!/bin/sh
                |[ -f '${down.path}' ] && { echo "ssh: connect to host box: Connection refused" >&2; exit 255; }
                |while [ "$1" != "--" ]; do printf '%s ' "$1" >> '${sshLog.path}'; shift; done
                |shift
                |printf 'host=%s\n' "$1" >> '${sshLog.path}'
                |shift
                |cd '${home.path}' && exec sh -c "$1"
                """.trimMargin(),
            )
            setExecutable(true)
        }
    }

    // ssh and git must never read the user's real configuration, keys or identity.
    private val local =
        JvmProcessLauncher(
            mapOf(
                "PATH" to bin.path + File.pathSeparator + (System.getenv("PATH") ?: ""),
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
    private val ssh = SshProcessLauncher(local, "me@box", port = 2222)

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun argumentsSurviveTheRemoteShellVerbatim() =
        runBlocking {
            val dir = File(root, "it's a dir").apply { mkdirs() }
            val tricky = listOf("a b", "it's", "\$HOME", "`id`", "; rm -rf /", "-n", "")
            val result =
                ssh.run(
                    ProcessSpec(
                        "sh",
                        listOf("-c", "pwd; printf '[%s]\\n' \"\$@\"; printf '%s\\n' \"\$OCTO_X\"", "sh") + tricky,
                        workingDir = dir.path,
                        env = mapOf("OCTO_X" to "x'y \$z"),
                    ),
                )
            assertEquals(0, result.exitCode, result.stderr)
            val lines = result.stdout.lines()
            assertEquals(dir.path, lines.first())
            assertEquals(tricky.map { "[$it]" }, lines.subList(1, tricky.size + 1))
            assertEquals("x'y \$z", lines.last())
            assertEquals("-T -o BatchMode=yes -p 2222 host=me@box", sshLog.readLines().first())
        }

    @Test
    fun whichLooksOnTheRemoteMachineAndReportsAnUnreachableHost() =
        runBlocking {
            assertTrue(ssh.which("sh")!!.endsWith("/sh"))
            assertNull(ssh.which("octo-no-such-command"))
            down.writeText("")
            val error = assertFailsWith<SshException> { ssh.which("sh") }
            assertTrue("Connection refused" in error.message!!)
        }

    @Test
    fun gitWorktreesWorkThroughTheLauncher() =
        runBlocking {
            val git = Git(ssh)
            val repo = File(root, "repo").apply { mkdirs() }
            git.run(repo.path, listOf("init", "-q", "-b", "main"))
            File(repo, "a.txt").writeText("one\n")
            git.run(repo.path, listOf("add", "."))
            git.run(repo.path, listOf("commit", "-q", "-m", "init"))

            val workspace = GitWorkspace(git, ShellDirectories(ssh))
            assertEquals(repo.path, workspace.repoRoot(repo.path))
            val worktree = File(root, "remote worktrees/repo/t1")
            workspace.createWorktree(repo.path, worktree.path, "octo/t1", "main")
            assertFalse(workspace.isDirty(worktree.path))
            File(worktree, "new.txt").writeText("fresh\n")
            assertTrue(workspace.isDirty(worktree.path))
            assertEquals(listOf("new.txt"), workspace.diff(worktree.path, "main").files.map { it.path })

            worktree.deleteRecursively()
            assertFalse(workspace.isDirty(worktree.path), "a missing worktree is checked over the launcher too")
        }

    @Test
    fun rejectsValuesThatSshWouldReadAsOptions() {
        assertFailsWith<IllegalArgumentException> { SshProcessLauncher(local, "-oProxyCommand=touch /tmp/x") }
        assertFailsWith<IllegalArgumentException> { SshProcessLauncher(local, "box; id") }
        assertFailsWith<IllegalArgumentException> { SshProcessLauncher(local, "box", port = 0) }
        assertFailsWith<IllegalArgumentException> {
            SshProcessLauncher.remoteCommand(ProcessSpec("env", env = mapOf("A;B" to "1")))
        }
    }
}
