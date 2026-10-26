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
import octo.core.paths.currentHostOs
import octo.core.process.ProcessLauncher

/** Every adapter Octo ships; the registry drops the ones whose feature flag is off. */
fun builtInAdapters(launcher: ProcessLauncher): List<AgentAdapter> =
    listOf(
        ClaudeAdapter(launcher),
        CodexAdapter(launcher),
        CursorAdapter(launcher),
        GrokAdapter(launcher),
        AntigravityAdapter(launcher, currentHostOs()),
        DemoAdapter(),
    )

fun adapterRegistry(
    launcher: ProcessLauncher,
    flags: FeatureFlags,
): AdapterRegistry = AdapterRegistry(builtInAdapters(launcher), flags)
