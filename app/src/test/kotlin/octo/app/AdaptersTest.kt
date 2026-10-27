package octo.app

import octo.adapters.antigravity.AntigravityAdapter
import octo.adapters.claude.ClaudeAdapter
import octo.adapters.codex.CodexAdapter
import octo.adapters.cursor.CursorAdapter
import octo.adapters.demo.DemoAdapter
import octo.adapters.grok.GrokAdapter
import octo.adapters.kimi.KimiAdapter
import octo.core.agent.AgentId
import octo.core.flags.FeatureFlags
import octo.core.plugins.PluginAgent
import octo.core.plugins.PluginManifest
import octo.core.process.JvmProcessLauncher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdaptersTest {
    @Test
    fun registryHoldsBuiltInAdapters() {
        val registry = adapterRegistry(JvmProcessLauncher(emptyMap()), FeatureFlags())
        assertNotNull(registry[ClaudeAdapter.ID])
        assertNotNull(registry[CodexAdapter.ID])
        assertNotNull(registry[CursorAdapter.ID])
        assertNotNull(registry[GrokAdapter.ID])
        assertNotNull(registry[KimiAdapter.ID])
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

    @Test
    fun pluginAgentsNeverTakeAnIdThatIsInUse() {
        fun agent(id: String) = PluginAgent(id, id, "$id-acp", emptyList(), "1.0")
        val first = PluginManifest(1, "first", "First", "1", listOf(agent("claude"), agent("helper")))
        val second = PluginManifest(1, "second", "Second", "1", listOf(agent("helper"), agent("other")))
        val launcher = JvmProcessLauncher(emptyMap())

        val agents = pluginAdapters(listOf(first, second), launcher, setOf(ClaudeAdapter.ID))
        val registry = adapterRegistry(launcher, FeatureFlags(), agents.adapters)

        assertEquals(listOf(AgentId("helper"), AgentId("other")), agents.adapters.map { it.id })
        assertEquals("helper (plugin First)", registry[AgentId("helper")]?.displayName)
        assertEquals(2, agents.warnings.size, agents.warnings.toString())
        assertTrue(agents.warnings[0].contains("'claude' of plugin first"))
        assertTrue(agents.warnings[1].contains("'helper' of plugin second"))
    }
}
