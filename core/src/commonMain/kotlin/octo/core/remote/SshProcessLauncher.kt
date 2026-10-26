package octo.core.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.IOException
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import octo.core.process.CommandPath
import octo.core.process.ProcessLauncher
import octo.core.process.ProcessSpec
import octo.core.process.RunningProcess
import octo.core.process.run
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Runs processes on another machine through the user's own `ssh` client, so keys, agents and
 * `~/.ssh/config` stay theirs and Octo never handles a credential. The remote login shell must be
 * POSIX (sh, bash, zsh): the command line is built with POSIX single quotes.
 *
 * With [sharing], calls reuse one master connection that this launcher starts and owns.
 */
class SshProcessLauncher(
    private val local: ProcessLauncher,
    private val host: String,
    private val port: Int? = null,
    command: String = "ssh",
    private val sharing: SharedConnection? = null,
) : ProcessLauncher {
    private val ssh = CommandPath(local, command) { SshNotFoundException() }
    private val masterLock = Mutex()
    private var master: RunningProcess? = null
    private var lastMasterStart: TimeSource.Monotonic.ValueTimeMark? = null
    private var closed = false

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

    /** Ends the shared master connection; later calls connect directly. */
    suspend fun close() {
        masterLock.withLock {
            closed = true
            master?.kill()
            master = null
        }
    }

    private suspend fun sshSpec(remote: String): ProcessSpec {
        sharing?.let { startMaster(it) }
        // With no socket yet ssh connects directly, so a missing or dead master only costs the handshake.
        val shared = sharing?.let { listOf("-o", "ControlMaster=no", "-o", "ControlPath=${it.controlPath}") }
        return ProcessSpec(ssh.get(), options() + shared.orEmpty() + listOf("--", host, remote))
    }

    // BatchMode makes ssh fail instead of prompting, since Octo has no terminal to ask for a password.
    private fun options() =
        listOf("-T", "-o", "BatchMode=yes") + (port?.let { listOf("-p", it.toString()) } ?: emptyList())

    private suspend fun startMaster(shared: SharedConnection) {
        masterLock.withLock {
            val due = lastMasterStart?.let { it.elapsedNow() >= shared.retry } ?: true
            if (closed || master != null || !due) return@withLock
            lastMasterStart = TimeSource.Monotonic.markNow()
            // ControlPersist=no keeps the master in the foreground, so it is ours to watch and to end.
            val spec =
                ProcessSpec(
                    ssh.get(),
                    listOf("-n", "-N") + options() +
                        listOf("-o", "ControlMaster=yes", "-o", "ControlPersist=no") +
                        listOf("-o", "ControlPath=${shared.controlPath}", "--", host),
                )
            val process =
                try {
                    // A socket left by a crashed Octo would keep the new master from listening.
                    shared.fs.delete(Path(shared.controlPath), mustExist = false)
                    local.launch(spec)
                } catch (ignored: IOException) {
                    // Without a master every call connects on its own, as it would with sharing off.
                    return@withLock
                }
            master = process
            shared.scope.launch {
                process.awaitExit()
                masterLock.withLock { if (master === process) master = null }
            }
        }
    }

    companion object {
        private const val SSH_FAILED = 255
        private val PORTS = 1..65_535
        private val HOST = Regex("[A-Za-z0-9._@:\\[\\]-]+")
        private val ENV_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")

        // ssh binds `<path>.` plus 16 random characters first (OpenSSH mux.c), and macOS allows
        // 104 bytes for a socket path, the terminating NUL included.
        private const val SOCKET_PATH_BYTES = 104
        private const val TEMP_SUFFIX = 17
        private const val MAX_CONTROL_PATH = SOCKET_PATH_BYTES - 1 - TEMP_SUFFIX

        // No space (ssh splits the option on it), `%` or `~` (ssh expands them).
        private val CONTROL_PATH = Regex("/[A-Za-z0-9._/-]+")

        /** A leading `-` would make ssh read the host as an option, such as `-oProxyCommand=…`. */
        fun isValidHost(host: String): Boolean = host.matches(HOST) && !host.startsWith("-")

        fun isValidPort(port: Int): Boolean = port in PORTS

        fun isValidControlPath(path: String): Boolean = path.matches(CONTROL_PATH) && path.length <= MAX_CONTROL_PATH

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

/**
 * Where a machine's master connection listens. [scope] watches the master; [retry] keeps an
 * unreachable host from getting a new master on every call.
 */
class SharedConnection(
    val controlPath: String,
    val scope: CoroutineScope,
    val retry: Duration = 30.seconds,
    internal val fs: FileSystem = SystemFileSystem,
) {
    init {
        require(SshProcessLauncher.isValidControlPath(controlPath)) { "Invalid SSH control path '$controlPath'" }
    }
}

/** POSIX single quotes: nothing inside is special, and a quote is closed, escaped and reopened. */
fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

class SshException(
    host: String,
    stderr: String,
) : IllegalStateException("Could not reach $host over SSH: $stderr")

class SshNotFoundException : IllegalStateException("ssh is not installed or not on PATH")
