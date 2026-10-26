package octo.adapters.plugin

import kotlinx.coroutines.Dispatchers
import octo.adapters.base.AcpAgentAdapter
import octo.core.agent.AgentId
import octo.core.agent.SessionSpec
import octo.core.plugins.PluginAgent
import octo.core.process.ProcessLauncher
import kotlin.coroutines.CoroutineContext

/**
 * An ACP agent a plugin declared. The command is a program on PATH and the arguments are fixed by
 * the manifest; the plugin itself never runs, it only said how to start the agent.
 */
class PluginAgentAdapter(
    launcher: ProcessLauncher,
    private val agent: PluginAgent,
    /** The plugin's own name, so the user sees where an agent came from. */
    plugin: String,
    context: CoroutineContext = Dispatchers.Default,
) : AcpAgentAdapter(launcher, context) {
    override val id = AgentId(agent.id)
    override val displayName = "${agent.displayName} (plugin $plugin)"
    override val binary = agent.command
    override val verifiedVersion = agent.verifiedVersion

    override fun buildArgs(spec: SessionSpec): List<String> = agent.args
}
