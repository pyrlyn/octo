package octo.core.orchestrator

import kotlinx.io.files.Path
import kotlinx.io.files.SystemTemporaryDirectory
import octo.core.agent.AdapterRegistry
import octo.core.agent.AgentAdapter
import octo.core.agent.AgentIcon
import octo.core.agent.AgentId
import octo.core.agent.AgentProbe
import octo.core.agent.AgentSession
import octo.core.agent.Capability
import octo.core.agent.SessionSpec
import octo.core.flags.FeatureFlags
import octo.core.io.AtomicFile
import octo.core.terms.TermsGate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AgentCatalogTest {
    // probe() throws, so a lookup that probed the binary would fail the test.
    private class StubAdapter(
        name: String,
        override val icon: AgentIcon?,
    ) : AgentAdapter {
        override val id = AgentId(name)
        override val displayName = name
        override val capabilities = emptySet<Capability>()

        override suspend fun probe(): AgentProbe = error("not used")

        override suspend fun start(spec: SessionSpec): AgentSession = error("not used")
    }

    private val glyph = AgentIcon("<svg/>")
    private val terms = TermsGate(AtomicFile(Path(SystemTemporaryDirectory, "octo-unused-terms.json")))

    private fun registry(vararg adapters: AgentAdapter) = AdapterRegistry(adapters.toList(), FeatureFlags())

    @Test
    fun anIconComesFromTheAdapterWithoutAProbe() {
        val catalog = RegistryAgentCatalog(registry(StubAdapter("claude", glyph), StubAdapter("plain", null)), terms)
        assertEquals(glyph, catalog.icon(AgentId("claude")))
        assertNull(catalog.icon(AgentId("plain")))
        assertNull(catalog.icon(AgentId("unknown")))
    }

    @Test
    fun anAgentOnlyOnARemoteMachineStillHasItsIcon() {
        val catalog = RegistryAgentCatalog(registry(), terms, mapOf("box" to registry(StubAdapter("codex", glyph))))
        assertEquals(glyph, catalog.icon(AgentId("codex")))
    }
}
