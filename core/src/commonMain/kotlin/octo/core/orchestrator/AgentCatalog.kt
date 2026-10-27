package octo.core.orchestrator

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import octo.core.agent.AdapterRegistry
import octo.core.agent.AgentIcon
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
    val icon: AgentIcon? = null,
)

/** The agents the user can pick from, and their terms; the UI's only view of adapters. */
interface AgentCatalog {
    /** Remote machine ids a task can run on; empty unless the `remote-ssh` flag is on. */
    val machines: List<String>

    /** Every agent registered for [machine] (null for this computer), probed in parallel there. */
    suspend fun agents(machine: String? = null): List<AgentInfo>

    suspend fun pendingTerms(id: AgentId): VendorTerms?

    suspend fun acknowledgeTerms(id: AgentId)
}

class RegistryAgentCatalog(
    private val registry: AdapterRegistry,
    private val terms: TermsGate,
    private val remote: Map<String, AdapterRegistry> = emptyMap(),
) : AgentCatalog {
    override val machines: List<String> = remote.keys.sorted()

    override suspend fun agents(machine: String?): List<AgentInfo> =
        coroutineScope {
            val registry = if (machine == null) registry else remote[machine] ?: throw UnknownMachineException(machine)
            registry.adapters
                .map { adapter ->
                    async {
                        AgentInfo(
                            adapter.id,
                            adapter.displayName,
                            adapter.capabilities,
                            adapter.probe(),
                            adapter.icon,
                        )
                    }
                }.awaitAll()
        }

    override suspend fun pendingTerms(id: AgentId): VendorTerms? = terms.pending(adapter(id))

    override suspend fun acknowledgeTerms(id: AgentId) = terms.acknowledge(adapter(id))

    // Terms belong to the vendor, not the machine, so any registry that has the agent will do.
    private fun adapter(id: AgentId) =
        registry[id] ?: remote.values.firstNotNullOfOrNull { it[id] } ?: throw UnknownAgentException(id)
}
