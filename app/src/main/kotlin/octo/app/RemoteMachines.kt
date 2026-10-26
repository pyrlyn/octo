package octo.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.io.files.Path
import octo.core.config.MachineConfig
import octo.core.flags.FeatureFlags
import octo.core.git.Git
import octo.core.git.GitWorkspace
import octo.core.git.ShellDirectories
import octo.core.orchestrator.Machine
import octo.core.orchestrator.RemoteMachine
import octo.core.process.ProcessLauncher
import octo.core.remote.SharedConnection
import octo.core.remote.SshProcessLauncher
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.Path as JavaPath

data class RemoteMachines(
    val machines: Map<String, Machine>,
    /** Entries that were skipped, and why. */
    val warnings: List<String>,
    internal val launchers: List<SshProcessLauncher> = emptyList(),
) {
    /** Ends the shared SSH connections; call it after the agents on them have stopped. */
    suspend fun close() = launchers.forEach { it.close() }
}

/**
 * One machine per valid config entry; a bad entry is reported and skipped, never fatal. With
 * [sockets], each machine shares one SSH connection whose master runs in [scope].
 */
fun remoteMachines(
    configs: List<MachineConfig>,
    launcher: ProcessLauncher,
    flags: FeatureFlags,
    sockets: Path? = null,
    scope: CoroutineScope? = null,
): RemoteMachines {
    val machines = mutableMapOf<String, Machine>()
    val warnings = mutableListOf<String>()
    val launchers = mutableListOf<SshProcessLauncher>()
    val dir = scope?.let { sockets?.let { socketDir(it, warnings) } }
    configs.forEach { config ->
        val problem = config.problem() ?: "Machine '${config.id}' is listed twice".takeIf { config.id in machines }
        if (problem != null) {
            warnings += "Skipping a machine in config.json: $problem"
            return@forEach
        }
        val controlPath = dir?.let { "$it/${config.id}" }
        if (controlPath != null && !SshProcessLauncher.isValidControlPath(controlPath)) {
            warnings += "Machine '${config.id}' opens an SSH connection per call: " +
                "ssh cannot use the socket path $controlPath"
        }
        val sharing =
            scope?.let { s ->
                controlPath?.takeIf { SshProcessLauncher.isValidControlPath(it) }?.let { SharedConnection(it, s) }
            }
        val ssh = SshProcessLauncher(launcher, config.host, config.port, sharing = sharing)
        launchers += ssh
        val workspace = GitWorkspace(Git(ssh), ShellDirectories(ssh))
        machines[config.id] = RemoteMachine(remoteAdapterRegistry(ssh, flags), workspace, ssh, config.worktrees)
    }
    return RemoteMachines(machines, warnings, launchers)
}

/**
 * Creates the socket directory readable by this user only. Anyone who can reach a control socket
 * can run commands as the user on the remote machine, so a directory that is not ours is refused.
 */
internal fun socketDir(
    sockets: Path,
    warnings: MutableList<String>,
): String? {
    val dir = JavaPath.of(sockets.toString())
    val problem =
        try {
            Files.createDirectories(dir, PosixFilePermissions.asFileAttribute(PRIVATE))
            when {
                !Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS) -> {
                    "it is not a directory"
                }

                Files.getOwner(dir, LinkOption.NOFOLLOW_LINKS).name != System.getProperty("user.name") -> {
                    "it belongs to another user"
                }

                else -> {
                    // Ours, so a looser mode left by hand can safely be tightened.
                    Files.setPosixFilePermissions(dir, PRIVATE)
                    null
                }
            }
        } catch (e: IOException) {
            e.message ?: e.toString()
        } catch (e: UnsupportedOperationException) {
            e.message ?: e.toString()
        }
    if (problem == null) return dir.toString()
    warnings += "Remote machines open an SSH connection per call: socket folder $dir: $problem"
    return null
}

private val PRIVATE = PosixFilePermissions.fromString("rwx------")
