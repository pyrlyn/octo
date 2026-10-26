package octo.app

import octo.adapters.antigravity.AntigravityAdapter
import octo.adapters.claude.ClaudeAdapter
import octo.adapters.codex.CodexAdapter
import octo.adapters.cursor.CursorAdapter
import octo.adapters.demo.DemoAdapter
import octo.adapters.grok.GrokAdapter
import octo.core.agent.AdapterRegistry
import octo.core.agent.AgentAdapter
import octo.core.flags.FeatureFlags
import octo.core.paths.HostOs
import octo.core.paths.currentHostOs
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
): AdapterRegistry = AdapterRegistry(builtInAdapters(launcher), flags)

/**
 * Agents on a remote machine, which is taken to be Linux. The demo agent writes through this
 * computer's file system, so it cannot run there.
 */
fun remoteAdapterRegistry(
    launcher: ProcessLauncher,
    flags: FeatureFlags,
): AdapterRegistry = AdapterRegistry(builtInAdapters(launcher, HostOs.Linux).filterNot { it is DemoAdapter }, flags)
