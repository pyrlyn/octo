package octo.adapters.antigravity

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import octo.core.agent.AdapterRegistry
import octo.core.agent.AgentEvent
import octo.core.agent.AgentSessionRef
import octo.core.agent.Role
import octo.core.agent.SessionSpec
import octo.core.agent.StopReason
import octo.core.flags.FeatureFlags
import octo.core.paths.HostOs
import octo.testing.Events
import octo.testing.FakeProcessLauncher
import octo.testing.readFixture
import octo.testing.replay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AntigravityAdapterTest {
    private fun TestScope.adapter(
        os: HostOs,
        binaries: Map<String, String> = mapOf("agy_acp_server.par" to "/opt/agy/agy_acp_server.par"),
    ): Pair<AntigravityAdapter, FakeProcessLauncher> {
        val launcher =
            FakeProcessLauncher(backgroundScope, binaries) {
                replay(readFixture("src/commonTest/fixtures/turn.transcript"))
            }
        return AntigravityAdapter(launcher, os, StandardTestDispatcher(testScheduler)) to launcher
    }

    @Test
    fun runsATurnOverAcp() =
        runTest {
            val (adapter, launcher) = adapter(HostOs.MacOs)
            val session = adapter.start(SessionSpec("/repo"))
            assertEquals("/opt/agy/agy_acp_server.par", launcher.launched.last().command)
            assertEquals(emptyList(), launcher.launched.last().args)
            val events = Events(backgroundScope, session)
            session.prompt("Hi")
            val all = events.drain()

            assertEquals(AgentEvent.SessionReady(AgentSessionRef("agy-1")), all[0])
            assertEquals(
                AgentEvent.Message(Role.Assistant, "Hello."),
                all.filterIsInstance<AgentEvent.Message>().single(),
            )
            assertEquals(StopReason.EndTurn, all.filterIsInstance<AgentEvent.TurnEnded>().single().stopReason)
            assertEquals(AgentEvent.Exited(0), all.last())
        }

    @Test
    fun passesTheRegistryArgumentOnLinuxAndFindsTheWindowsName() =
        runTest {
            val (linux, linuxLauncher) = adapter(HostOs.Linux)
            linux.start(SessionSpec("/repo")).close()
            assertEquals(listOf("--uid="), linuxLauncher.launched.last().args)

            val (windows, windowsLauncher) =
                adapter(HostOs.Windows, mapOf("agy_acp_server" to "C:\\agy\\agy_acp_server.exe"))
            windows.start(SessionSpec("/repo")).close()
            assertEquals("C:\\agy\\agy_acp_server.exe", windowsLauncher.launched.last().command)
        }

    @Test
    fun registeredOnlyBehindItsFlagAndCarriesTerms() =
        runTest {
            val (adapter, _) = adapter(HostOs.MacOs)
            assertNull(AdapterRegistry(listOf(adapter), FeatureFlags())[AntigravityAdapter.ID])
            val enabled = FeatureFlags(mapOf(AntigravityAdapter.FLAG.id to true))
            assertNotNull(AdapterRegistry(listOf(adapter), enabled)[AntigravityAdapter.ID])
            assertEquals(AntigravityAdapter.TERMS, adapter.terms)
        }
}
