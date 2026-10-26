package octo.app

import octo.adapters.antigravity.AntigravityAdapter
import octo.adapters.claude.ClaudeAdapter
import octo.adapters.codex.CodexAdapter
import octo.adapters.cursor.CursorAdapter
import octo.adapters.demo.DemoAdapter
import octo.adapters.grok.GrokAdapter
import octo.adapters.plugin.PluginAgentAdapter
import octo.core.agent.AdapterRegistry
import octo.core.agent.AgentAdapter
import octo.core.agent.AgentId
import octo.core.flags.FeatureFlags
import octo.core.paths.HostOs
import octo.core.paths.currentHostOs
import octo.core.plugins.PluginManifest
import octo.core.process.ProcessLauncher

/** Every adapter Octo ships; the registry drops the ones whose feature flag is off. */
fun builtInAdapters(
    launcher: ProcessLauncher,
    os: HostOs = currentHostOs(),
): List<AgentAdapter> =
    listOf(
        ClaudeAdapter(launcher),
        CodexAdapter(launcher),
        CursorAdapter(launcher),
        GrokAdapter(launcher),
        AntigravityAdapter(launcher, os),
        DemoAdapter(),
    )

fun adapterRegistry(
    launcher: ProcessLauncher,
    flags: FeatureFlags,
    /** Plugin agents, from [pluginAdapters]. */
    extra: List<AgentAdapter> = emptyList(),
): AdapterRegistry = AdapterRegistry(builtInAdapters(launcher) + extra, flags)

/** The agents plugins declare, and a warning for each one that could not be added. */
data class PluginAdapters(
    val adapters: List<AgentAdapter>,
    val warnings: List<String>,
)

/**
 * One adapter per agent the [manifests] declare. An id that is already [taken] is skipped, so a
 * plugin can never stand in for a built-in agent or for another plugin's.
 */
fun pluginAdapters(
    manifests: List<PluginManifest>,
    launcher: ProcessLauncher,
    taken: Set<AgentId>,
): PluginAdapters {
    val ids = taken.toMutableSet()
    val adapters = mutableListOf<AgentAdapter>()
    val warnings = mutableListOf<String>()
    for (manifest in manifests) {
        for (agent in manifest.agents) {
            if (ids.add(AgentId(agent.id))) {
                adapters += PluginAgentAdapter(launcher, agent, manifest.name)
            } else {
                warnings += "Skipping agent '${agent.id}' of plugin ${manifest.id}: another agent has that id"
            }
        }
    }
    return PluginAdapters(adapters, warnings)
}

/**
 * Agents on a remote machine, which is taken to be Linux. The demo agent writes through this
 * computer's file system, so it cannot run there.
 */
fun remoteAdapterRegistry(
    launcher: ProcessLauncher,
    flags: FeatureFlags,
): AdapterRegistry = AdapterRegistry(builtInAdapters(launcher, HostOs.Linux).filterNot { it is DemoAdapter }, flags)
