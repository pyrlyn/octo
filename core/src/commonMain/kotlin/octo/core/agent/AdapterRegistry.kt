package octo.core.agent

import octo.core.flags.FeatureFlags

/** The adapters Octo can run, minus those whose feature flag is off. */
class AdapterRegistry(
    adapters: List<AgentAdapter>,
    flags: FeatureFlags,
) {
    init {
        val duplicates = adapters.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "Duplicate adapter ids: ${duplicates.joinToString { it.value }}" }
    }

    val adapters: List<AgentAdapter> = adapters.filter { adapter -> adapter.featureFlag?.let(flags::isEnabled) ?: true }

    private val byId = this.adapters.associateBy { it.id }

    operator fun get(id: AgentId): AgentAdapter? = byId[id]
}
