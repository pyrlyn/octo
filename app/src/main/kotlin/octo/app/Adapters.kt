package octo.app

import octo.adapters.claude.ClaudeAdapter
import octo.core.agent.AdapterRegistry
import octo.core.agent.AgentAdapter
import octo.core.flags.FeatureFlags
import octo.core.process.ProcessLauncher

/** Every adapter Octo ships; the registry drops the ones whose feature flag is off. */
fun builtInAdapters(launcher: ProcessLauncher): List<AgentAdapter> =
    listOf(
        ClaudeAdapter(launcher),
    )

fun adapterRegistry(
    launcher: ProcessLauncher,
    flags: FeatureFlags,
): AdapterRegistry = AdapterRegistry(builtInAdapters(launcher), flags)
