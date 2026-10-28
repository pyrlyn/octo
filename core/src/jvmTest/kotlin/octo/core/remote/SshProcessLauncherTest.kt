package octo.core.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class SshProcessLauncherTest {
    private val root = Files.createTempDirectory("octo-ssh").toFile().canonicalFile
    private val home = File(root, "home").apply { mkdirs() }
    private val bin = File(root, "bin").apply { mkdirs() }
    private val sshLog = File(root, "ssh.log")
    private val down = File(root, "down")
    private val dies = File(root, "dies")
    private val masterPid = File(root, "master.pid")

    // Short on purpose: the usual temp folder can leave no room under the socket path limit.
    private val sockets =
        Files
            .createTempDirectory(
                java.nio.file.Path
                    .of("/tmp"),
                "os",
            ).toFile()
    private val socket = File(sockets, "s").path
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // A stand-in for `ssh`: logs its options and host, then runs the remote command with a local
        // POSIX shell, the way sshd hands it to the login shell. A master (-N) records its pid and
        // waits, or exits at once when told to die. Each log line is one write, since a master and a
        // client log at the same time.
        File(bin, "ssh").apply {
            writeText(
                """
                |#!/bin/sh
                |[ -f '${down.path}' ] && { echo "ssh: connect to host box: Connection refused" >&2; exit 255; }
                |line=''
                |while [ "$1" != "--" ]; do [ "$1" = "-N" ] && master=1; line="${'$'}line$1 "; shift; done
                |shift
                |printf '%shost=%s\n' "${'$'}line" "$1" >> '${sshLog.path}'
                |shift
                |if [ -n "${'$'}master" ]; then
                |  [ -f '${dies.path}' ] && exit 0
                |  echo $$ > '${masterPid.path}'
                |  exec sleep 600
                |fi
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
        scope.cancel()
        root.deleteRecursively()
        sockets.deleteRecursively()
    }

    private fun shared(retry: Duration = 30.seconds) =
        SshProcessLauncher(local, "me@box", port = 2222, sharing = SharedConnection(socket, scope, retry))

    private fun logged(): List<String> = if (sshLog.exists()) sshLog.readLines() else emptyList()

    private fun masters() = logged().filter { "-N" in it.split(" ") }

    private suspend fun eventually(
        what: String,
        check: suspend () -> Boolean,
    ) {
        repeat(WAIT_STEPS) {
            if (check()) return
            delay(WAIT_STEP_MS)
        }
        throw AssertionError("timed out waiting until $what")
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
    fun callsShareOneMasterThatCloseEnds() =
        runBlocking {
            File(socket).writeText("left by a crash")
            val ssh = shared()
            try {
                assertEquals(0, ssh.run(ProcessSpec("true")).exitCode)
                assertEquals(0, ssh.run(ProcessSpec("true")).exitCode)
                eventually("the master runs") { masterPid.exists() }

                assertEquals(
                    "-n -N -T -o BatchMode=yes -p 2222 -o ControlMaster=yes -o ControlPersist=no " +
                        "-o ControlPath=$socket host=me@box",
                    masters().single(),
                )
                val client = "-T -o BatchMode=yes -p 2222 -o ControlMaster=no -o ControlPath=$socket host=me@box"
                assertEquals(listOf(client, client), logged() - masters().toSet())
                assertFalse(File(socket).exists(), "a stale socket is removed before the master starts")
            } finally {
                ssh.close()
            }
            val pid = masterPid.readText().trim().toLong()
            eventually("close ends the master") { ProcessHandle.of(pid).map { !it.isAlive }.orElse(true) }

            masterPid.delete()
            assertEquals(0, ssh.run(ProcessSpec("true")).exitCode, "a closed launcher still connects directly")
            assertEquals(1, masters().size)
        }

    @Test
    fun aMasterThatExitsIsStartedAgainButNotOnEveryCall() =
        runBlocking {
            dies.writeText("")
            val eager = shared(retry = Duration.ZERO)
            eventually("a second master starts") {
                eager.run(ProcessSpec("true"))
                masters().size >= 2
            }
            eager.close()

            sshLog.delete()
            val patient = shared()
            repeat(3) {
                patient.run(ProcessSpec("true"))
                delay(WAIT_STEP_MS)
            }
            patient.close()
            assertEquals(1, masters().size, "an unreachable host must not get a master per call")
        }

    @Test
    fun gitReachesAClonePathWithTheSameSshAndThePortInTheUrl() =
        runBlocking {
            assertFailsWith<IllegalArgumentException> { ssh.at("srv/app") }
            val remote = ssh.at("/srv/app")
            assertEquals("ssh://me@box:2222/srv/app", remote.url)
            assertEquals("'${File(bin, "ssh").path}' '-o' 'BatchMode=yes'", remote.sshCommand)
        }

    @Test
    fun controlPathsSshWouldMisreadAreRejected() {
        assertTrue(SshProcessLauncher.isValidControlPath("/run/user/1000/octo/box-1"))
        assertTrue(SshProcessLauncher.isValidControlPath("/" + "a".repeat(85)))
        assertFalse(SshProcessLauncher.isValidControlPath("/" + "a".repeat(86)), "no room for ssh's temp name")
        assertFalse(SshProcessLauncher.isValidControlPath("octo/box"))
        assertFalse(SshProcessLauncher.isValidControlPath("/Library/Application Support/Octo/box"))
        assertFalse(SshProcessLauncher.isValidControlPath("/tmp/%h"))
        assertFalse(SshProcessLauncher.isValidControlPath("/tmp/~/box"))
        assertFailsWith<IllegalArgumentException> { SharedConnection("/tmp/a b", scope) }
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

    private companion object {
        const val WAIT_STEPS = 100
        const val WAIT_STEP_MS = 50L
    }
}
