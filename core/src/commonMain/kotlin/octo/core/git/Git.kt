package octo.core.git

import octo.core.process.CommandPath
import octo.core.process.ProcessLauncher
import octo.core.process.ProcessSpec
import octo.core.process.run

/** The `git` CLI. There is no git library with worktree support for every target (architecture §2). */
class Git(
    private val launcher: ProcessLauncher,
    command: String = "git",
) {
    private val path = CommandPath(launcher, command) { GitNotFoundException() }

    /** Runs `git -C [dir] [args]` and returns stdout; an exit code outside [okCodes] throws. */
    suspend fun run(
        dir: String,
        args: List<String>,
        okCodes: Set<Int> = setOf(0),
        env: Map<String, String> = emptyMap(),
    ): String {
        // Quoted paths would hide non-ASCII names from the diff parser.
        val full = listOf("-c", "core.quotepath=off", "-C", dir) + args
        val result = launcher.run(ProcessSpec(path.get(), full, env = env))
        if (result.exitCode !in okCodes) throw GitException(args, result.exitCode, result.stderr.trim())
        return result.stdout
    }
}

class GitException(
    args: List<String>,
    val exitCode: Int,
    val stderr: String,
) : IllegalStateException("git ${args.joinToString(" ")} failed ($exitCode): $stderr")

class GitNotFoundException : IllegalStateException("git is not installed or not on PATH")
