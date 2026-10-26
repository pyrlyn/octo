package octo.core.terms

import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import octo.core.agent.AgentAdapter
import octo.core.agent.AgentId
import octo.core.agent.AgentProbe
import octo.core.agent.AgentSession
import octo.core.agent.Capability
import octo.core.agent.SessionSpec
import octo.core.agent.VendorTerms
import octo.core.io.AtomicFile
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TermsGateTest {
    private class StubAdapter(
        override val terms: VendorTerms?,
    ) : AgentAdapter {
        override val id = AgentId("vendor")
        override val displayName = "Vendor"
        override val capabilities = emptySet<Capability>()

        override suspend fun probe(): AgentProbe = error("not used")

        override suspend fun start(spec: SessionSpec): AgentSession = error("not used")
    }

    private val terms = VendorTerms("1", "Read the terms.", "https://example.com/terms")

    private fun tempFile(): AtomicFile {
        val dir = Path(SystemTemporaryDirectory, "octo-terms-${Random.nextLong().toULong()}")
        return AtomicFile(Path(dir, "acknowledgements.json"))
    }

    @Test
    fun noticeShowsOnceAndSurvivesARestart() =
        runTest {
            val file = tempFile()
            val adapter = StubAdapter(terms)
            val gate = TermsGate(file)
            assertEquals(terms, gate.pending(adapter))
            assertFailsWith<TermsNotAcknowledgedException> { gate.requireAcknowledged(adapter) }

            gate.acknowledge(adapter)
            assertNull(gate.pending(adapter))
            assertNull(TermsGate(AtomicFile(file.path)).pending(adapter))
            file.path.parent?.let { dir ->
                SystemFileSystem.delete(file.path)
                SystemFileSystem.delete(dir)
            }
        }

    @Test
    fun newTermsVersionShowsAgain() =
        runTest {
            val file = tempFile()
            TermsGate(file).acknowledge(StubAdapter(terms))
            val updated = terms.copy(version = "2")
            assertEquals(updated, TermsGate(file).pending(StubAdapter(updated)))
        }

    @Test
    fun adapterWithoutTermsNeverWaitsAndBrokenFileShowsAgain() =
        runTest {
            val file = tempFile()
            assertNull(TermsGate(file).pending(StubAdapter(null)))
            file.write("not json")
            assertEquals(terms, TermsGate(file).pending(StubAdapter(terms)))
        }
}
