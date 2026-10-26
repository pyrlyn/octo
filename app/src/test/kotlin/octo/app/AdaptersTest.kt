package octo.app

import octo.adapters.antigravity.AntigravityAdapter
import octo.adapters.claude.ClaudeAdapter
import octo.adapters.codex.CodexAdapter
import octo.adapters.cursor.CursorAdapter
import octo.adapters.grok.GrokAdapter
import octo.core.flags.FeatureFlags
import octo.core.process.JvmProcessLauncher
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AdaptersTest {
    @Test
    fun registryHoldsBuiltInAdapters() {
        val registry = adapterRegistry(JvmProcessLauncher(emptyMap()), FeatureFlags())
        assertNotNull(registry[ClaudeAdapter.ID])
        assertNotNull(registry[CodexAdapter.ID])
        assertNotNull(registry[CursorAdapter.ID])
        assertNotNull(registry[GrokAdapter.ID])
        assertNull(registry[AntigravityAdapter.ID])
    }

    @Test
    fun antigravityNeedsItsFlag() {
        val flags = FeatureFlags(mapOf(AntigravityAdapter.FLAG.id to true))
        assertNotNull(adapterRegistry(JvmProcessLauncher(emptyMap()), flags)[AntigravityAdapter.ID])
    }
}
