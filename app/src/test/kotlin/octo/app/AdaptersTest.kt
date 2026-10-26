package octo.app

import octo.adapters.claude.ClaudeAdapter
import octo.adapters.codex.CodexAdapter
import octo.adapters.cursor.CursorAdapter
import octo.adapters.grok.GrokAdapter
import octo.core.flags.FeatureFlags
import octo.core.process.JvmProcessLauncher
import kotlin.test.Test
import kotlin.test.assertNotNull

class AdaptersTest {
    @Test
    fun registryHoldsBuiltInAdapters() {
        val registry = adapterRegistry(JvmProcessLauncher(emptyMap()), FeatureFlags())
        assertNotNull(registry[ClaudeAdapter.ID])
        assertNotNull(registry[CodexAdapter.ID])
        assertNotNull(registry[CursorAdapter.ID])
        assertNotNull(registry[GrokAdapter.ID])
    }
}
