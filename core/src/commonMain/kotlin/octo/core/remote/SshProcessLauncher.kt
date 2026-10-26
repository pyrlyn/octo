package octo.core.remote

import octo.core.process.CommandPath
import octo.core.process.ProcessLauncher
import octo.core.process.ProcessSpec
import octo.core.process.RunningProcess
import octo.core.process.run

/**
 * Runs processes on another machine through the user's own `ssh` client, so keys, agents and
 * `~/.ssh/config` stay theirs and Octo never handles a credential. The remote login shell must be
 * POSIX (sh, bash, zsh): the command line is built with POSIX single quotes.
 */
class SshProcessLauncher(
    private val local: ProcessLauncher,
    private val host: String,
    private val port: Int? = null,
    command: String = "ssh",
) : ProcessLauncher {
    private val ssh = CommandPath(local, command) { SshNotFoundException() }

    init {
        require(isValidHost(host)) { "Invalid SSH host '$host'" }
        require(port == null || isValidPort(port)) { "Invalid SSH port $port" }
    }

    override suspend fun which(command: String): String? {
        val result = local.run(sshSpec("command -v ${shellQuote(command)}"))
        // 255 is ssh's own failure; reporting it as "not installed" would hide an unreachable host.
        if (result.exitCode == SSH_FAILED) throw SshException(host, result.stderr.trim())
        return result.stdout
            .lineSequence()
            .firstOrNull()
            ?.trim()
            ?.takeIf { result.exitCode == 0 && it.isNotEmpty() }
    }

    override suspend fun launch(spec: ProcessSpec): RunningProcess = local.launch(sshSpec(remoteCommand(spec)))

    private suspend fun sshSpec(remote: String): ProcessSpec {
        // BatchMode makes ssh fail instead of prompting, since Octo has no terminal to ask for a password.
        val options = listOf("-T", "-o", "BatchMode=yes") + (port?.let { listOf("-p", it.toString()) } ?: emptyList())
        return ProcessSpec(ssh.get(), options + listOf("--", host, remote))
    }

    companion object {
        private const val SSH_FAILED = 255
        private val PORTS = 1..65_535
        private val HOST = Regex("[A-Za-z0-9._@:\\[\\]-]+")
        private val ENV_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")

        /** A leading `-` would make ssh read the host as an option, such as `-oProxyCommand=…`. */
        fun isValidHost(host: String): Boolean = host.matches(HOST) && !host.startsWith("-")

        fun isValidPort(port: Int): Boolean = port in PORTS

        /** `cd 'dir' && exec env 'K=V' 'command' 'arg'…`; exec keeps the process tree one level deep. */
        internal fun remoteCommand(spec: ProcessSpec): String {
            spec.env.keys.forEach { require(it.matches(ENV_NAME)) { "Invalid environment variable name '$it'" } }
            val env =
                if (spec.env.isEmpty()) {
                    emptyList()
                } else {
                    listOf("env") +
                        spec.env.map { (k, v) -> shellQuote("$k=$v") }
                }
            val exec = (listOf("exec") + env + shellQuote(spec.command) + spec.args.map(::shellQuote)).joinToString(" ")
            return spec.workingDir?.let { "cd ${shellQuote(it)} && $exec" } ?: exec
        }
    }
}

/** POSIX single quotes: nothing inside is special, and a quote is closed, escaped and reopened. */
fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

class SshException(
    host: String,
    stderr: String,
) : IllegalStateException("Could not reach $host over SSH: $stderr")

class SshNotFoundException : IllegalStateException("ssh is not installed or not on PATH")
