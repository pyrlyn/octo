package octo.app

import octo.core.config.MachineConfig
import octo.core.flags.FeatureFlags
import octo.core.git.Git
import octo.core.git.GitWorkspace
import octo.core.git.ShellDirectories
import octo.core.orchestrator.Machine
import octo.core.orchestrator.RemoteMachine
import octo.core.process.ProcessLauncher
import octo.core.remote.SshProcessLauncher

data class RemoteMachines(
    val machines: Map<String, Machine>,
    /** Entries that were skipped, and why. */
    val warnings: List<String>,
)

/** One machine per valid config entry; a bad entry is reported and skipped, never fatal. */
fun remoteMachines(
    configs: List<MachineConfig>,
    launcher: ProcessLauncher,
    flags: FeatureFlags,
): RemoteMachines {
    val machines = mutableMapOf<String, Machine>()
    val warnings = mutableListOf<String>()
    configs.forEach { config ->
        val problem = config.problem() ?: "Machine '${config.id}' is listed twice".takeIf { config.id in machines }
        if (problem != null) {
            warnings += "Skipping a machine in config.json: $problem"
            return@forEach
        }
        val ssh = SshProcessLauncher(launcher, config.host, config.port)
        val workspace = GitWorkspace(Git(ssh), ShellDirectories(ssh))
        machines[config.id] = RemoteMachine(remoteAdapterRegistry(ssh, flags), workspace, ssh, config.worktrees)
    }
    return RemoteMachines(machines, warnings)
}
