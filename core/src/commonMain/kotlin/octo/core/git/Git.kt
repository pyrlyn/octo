package octo.core.git

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import octo.core.process.ProcessLauncher
import octo.core.process.ProcessSpec
import octo.core.process.run

/** The `git` CLI. There is no git library with worktree support for every target (architecture §2). */
class Git(
    private val launcher: ProcessLauncher,
    private val command: String = "git",
) {
    private val lock = Mutex()
    private var resolved: String? = null

    /** Runs `git -C [dir] [args]` and returns stdout; an exit code outside [okCodes] throws. */
    suspend fun run(
        dir: String,
        args: List<String>,
        okCodes: Set<Int> = setOf(0),
    ): String {
        // Quoted paths would hide non-ASCII names from the diff parser.
        val full = listOf("-c", "core.quotepath=off", "-C", dir) + args
        val result = launcher.run(ProcessSpec(executable(), full))
        if (result.exitCode !in okCodes) throw GitException(args, result.exitCode, result.stderr.trim())
        return result.stdout
    }

    // Resolved through the launcher, so the PATH Octo was given wins over the JVM's own.
    private suspend fun executable(): String =
        lock.withLock {
            resolved ?: (launcher.which(command) ?: throw GitNotFoundException()).also { resolved = it }
        }
}

class GitException(
    args: List<String>,
    val exitCode: Int,
    val stderr: String,
) : IllegalStateException("git ${args.joinToString(" ")} failed ($exitCode): $stderr")

class GitNotFoundException : IllegalStateException("git is not installed or not on PATH")
