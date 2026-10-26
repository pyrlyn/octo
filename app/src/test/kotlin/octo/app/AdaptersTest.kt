package octo.app

import octo.adapters.antigravity.AntigravityAdapter
import octo.adapters.claude.ClaudeAdapter
import octo.adapters.codex.CodexAdapter
import octo.adapters.cursor.CursorAdapter
import octo.adapters.demo.DemoAdapter
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
        assertNull(registry[DemoAdapter.ID])
    }

    @Test
    fun flaggedAdaptersNeedTheirFlag() {
        val flags = FeatureFlags(mapOf(AntigravityAdapter.FLAG.id to true, DemoAdapter.FLAG.id to true))
        val registry = adapterRegistry(JvmProcessLauncher(emptyMap()), flags)
        assertNotNull(registry[AntigravityAdapter.ID])
        assertNotNull(registry[DemoAdapter.ID])
    }
}
