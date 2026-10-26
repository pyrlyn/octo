package octo.core.flags

import octo.core.agent.AdapterRegistry
import octo.core.agent.AgentAdapter
import octo.core.agent.AgentId
import octo.core.agent.AgentProbe
import octo.core.agent.AgentSession
import octo.core.agent.Capability
import octo.core.agent.SessionSpec
import octo.core.config.OctoConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeatureFlagsTest {
    private val gated = FeatureFlag("gated", "Gated adapter")
    private val needsPlan = FeatureFlag("planning", "Plan view", requires = setOf(Capability.Plan))

    private class StubAdapter(
        name: String,
        override val capabilities: Set<Capability> = emptySet(),
        override val featureFlag: FeatureFlag? = null,
    ) : AgentAdapter {
        override val id = AgentId(name)
        override val displayName = name

        override suspend fun probe(): AgentProbe = error("not used")

        override suspend fun start(spec: SessionSpec): AgentSession = error("not used")
    }

    @Test
    fun flagsDefaultToOff() {
        assertFalse(FeatureFlags().isEnabled(gated))
        assertTrue(FeatureFlags(mapOf("gated" to true)).isEnabled(gated))
    }

    @Test
    fun availabilityNeedsCapabilities() {
        val flags = FeatureFlags(mapOf("planning" to true))
        assertTrue(flags.isAvailable(needsPlan, StubAdapter("a", setOf(Capability.Plan))))
        assertFalse(flags.isAvailable(needsPlan, StubAdapter("b")))
    }

    @Test
    fun registryHidesAdaptersBehindDisabledFlags() {
        val adapters = listOf(StubAdapter("open"), StubAdapter("hidden", featureFlag = gated))
        val off = AdapterRegistry(adapters, FeatureFlags())
        assertEquals(listOf("open"), off.adapters.map { it.id.value })
        assertNull(off[AgentId("hidden")])
        assertNotNull(AdapterRegistry(adapters, FeatureFlags(mapOf("gated" to true)))[AgentId("hidden")])
    }

    @Test
    fun registryRejectsDuplicateIds() {
        assertFailsWith<IllegalArgumentException> {
            AdapterRegistry(listOf(StubAdapter("a"), StubAdapter("a")), FeatureFlags())
        }
    }

    @Test
    fun brokenConfigFallsBackToDefaults() {
        val parsed = OctoConfig.parse("{ not json")
        assertEquals(OctoConfig(), parsed.config)
        assertNotNull(parsed.warning)
        assertTrue(
            OctoConfig
                .parse("""{"flags":{"gated":true}}""")
                .config
                .featureFlags()
                .isEnabled(gated),
        )
    }
}
