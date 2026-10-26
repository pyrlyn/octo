package octo.core.flags

import octo.core.agent.AgentAdapter
import octo.core.agent.Capability

/** An optional feature. Declared next to the feature that owns it; off unless configured. */
data class FeatureFlag(
    val id: String,
    val description: String,
    val default: Boolean = false,
    val requires: Set<Capability> = emptySet(),
)

class FeatureFlags(
    private val values: Map<String, Boolean> = emptyMap(),
) {
    fun isEnabled(flag: FeatureFlag): Boolean = values[flag.id] ?: flag.default

    /** Enabled, and the adapter can do everything the feature needs. */
    fun isAvailable(
        flag: FeatureFlag,
        adapter: AgentAdapter,
    ): Boolean = isEnabled(flag) && adapter.capabilities.containsAll(flag.requires)
}
