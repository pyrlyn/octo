package octo.core.git

import octo.core.process.CommandPath
import octo.core.process.ProcessLauncher
import octo.core.process.ProcessSpec
import octo.core.process.run

/** The GitHub CLI. It signs in with its own login; Octo never sees or stores a token. */
class GitHubCli(
    private val launcher: ProcessLauncher,
    command: String = "gh",
) {
    private val path = CommandPath(launcher, command) { GitHubCliNotFoundException() }

    /** Runs `gh [args]` in [dir] and returns stdout; a non-zero exit throws. */
    suspend fun run(
        dir: String,
        args: List<String>,
    ): String {
        val result = launcher.run(ProcessSpec(path.get(), args, workingDir = dir))
        if (result.exitCode != 0) throw GitHubCliException(args, result.exitCode, result.stderr.trim())
        return result.stdout
    }
}

class GitHubCliException(
    args: List<String>,
    val exitCode: Int,
    val stderr: String,
) : IllegalStateException("gh ${args.take(2).joinToString(" ")} failed ($exitCode): $stderr")

class GitHubCliNotFoundException : IllegalStateException("gh is not installed or not on PATH")
