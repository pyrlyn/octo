package octo.core.orchestrator

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import octo.core.agent.AdapterRegistry
import octo.core.agent.AgentId
import octo.core.agent.AgentProbe
import octo.core.agent.Capability
import octo.core.agent.VendorTerms
import octo.core.terms.TermsGate

data class AgentInfo(
    val id: AgentId,
    val displayName: String,
    val capabilities: Set<Capability>,
    val probe: AgentProbe,
)

/** The agents the user can pick from, and their terms; the UI's only view of adapters. */
interface AgentCatalog {
    /** Every registered agent, probed in parallel. */
    suspend fun agents(): List<AgentInfo>

    suspend fun pendingTerms(id: AgentId): VendorTerms?

    suspend fun acknowledgeTerms(id: AgentId)
}

class RegistryAgentCatalog(
    private val registry: AdapterRegistry,
    private val terms: TermsGate,
) : AgentCatalog {
    override suspend fun agents(): List<AgentInfo> =
        coroutineScope {
            registry.adapters
                .map { adapter ->
                    async { AgentInfo(adapter.id, adapter.displayName, adapter.capabilities, adapter.probe()) }
                }.awaitAll()
        }

    override suspend fun pendingTerms(id: AgentId): VendorTerms? = terms.pending(adapter(id))

    override suspend fun acknowledgeTerms(id: AgentId) = terms.acknowledge(adapter(id))

    private fun adapter(id: AgentId) = registry[id] ?: throw UnknownAgentException(id)
}
