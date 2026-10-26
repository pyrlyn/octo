package octo.app

import octo.adapters.claude.ClaudeAdapter
import octo.core.flags.FeatureFlags
import octo.core.process.JvmProcessLauncher
import kotlin.test.Test
import kotlin.test.assertNotNull

class AdaptersTest {
    @Test
    fun registryHoldsClaude() {
        assertNotNull(adapterRegistry(JvmProcessLauncher(emptyMap()), FeatureFlags())[ClaudeAdapter.ID])
    }
}
