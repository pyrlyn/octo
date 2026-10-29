package octo.ui.vm

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import octo.core.agent.PermissionMode
import octo.core.config.AppearanceConfig
import octo.core.config.ConfigFile
import octo.core.config.MachineConfig
import octo.core.config.Transparency
import octo.core.flags.CoreFlags
import octo.core.flags.FeatureFlag
import octo.core.io.AtomicFile
import octo.testing.FakeCatalog
import octo.testing.FakeOctoCore
import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsViewModelTest {
    private val dir = Files.createTempDirectory("octo-ui-settings")
    private val path = dir.resolve("config.json")
    private val core = FakeOctoCore()
    private val adapterFlag = FeatureFlag("adapter-demo", "Demo adapter")
    private val opened = mutableListOf<String>()

    private fun kotlinx.coroutines.CoroutineScope.settings() =
        SettingsViewModel(
            ConfigFile(AtomicFile(Path(path.toString()))),
            FakeCatalog(core),
            CoreFlags.all + adapterFlag,
            dir.toString(),
            this,
            open = { opened += it },
        )

    @Test
    fun flagsAreSplitIntoSections() =
        runTest {
            val vm = backgroundScope.settings()
            assertEquals(listOf(adapterFlag), vm.flags(SettingsSection.Agents))
            assertEquals(listOf(CoreFlags.PLUGINS, CoreFlags.AGENT_MESSAGING), vm.flags(SettingsSection.Advanced))
            assertTrue(CoreFlags.REMOTE_ACCESS in vm.flags(SettingsSection.Devices))
            val general = vm.flags(SettingsSection.General)
            assertTrue(CoreFlags.USAGE in general)
            assertFalse(CoreFlags.REMOTE_ACCESS in general || CoreFlags.PLUGINS in general || adapterFlag in general)
            assertEquals(emptyList(), vm.flags(SettingsSection.Appearance))
        }

    @Test
    fun editsAreWrittenAndAskForARestartButAppearanceDoesNot() =
        runTest {
            val vm = backgroundScope.settings()
            vm.state.first { it.snapshot != null }

            vm.setAppearance(AppearanceConfig(transparency = Transparency.Opaque))
            val look = vm.state.first { it.config.appearance.transparency == Transparency.Opaque }
            assertFalse(look.restartNeeded, "appearance applies at once")

            vm.setFlag(CoreFlags.USAGE, true)
            vm.setPermissionMode(PermissionMode.Auto)
            val after = vm.state.first { it.config.defaultPermissionMode == PermissionMode.Auto }
            assertTrue(after.restartNeeded)
            assertTrue(after.config.featureFlags().isEnabled(CoreFlags.USAGE))
            assertTrue("\"usage\"" in path.readText())
        }

    @Test
    fun badValuesAreRefusedInOctosOwnWords() =
        runTest {
            val vm = backgroundScope.settings()
            vm.state.first { it.snapshot != null }
            assertEquals("Use the repository's absolute path", vm.saveWorktreeSetup("src/app", "npm ci"))
            assertEquals("remoteAccess.port must be a number", vm.savePort("eighty"))
            assertNotNull(vm.saveMachine(null, MachineConfig("Dev Box", "devbox")))

            assertNull(vm.saveMachine(null, MachineConfig("devbox", "me@devbox")))
            vm.state.first { it.config.machines.isNotEmpty() }
            assertEquals(
                "There is already a machine called 'devbox'",
                vm.saveMachine(null, MachineConfig("devbox", "other")),
            )
            assertNull(vm.saveMachine("devbox", MachineConfig("devbox", "me@devbox", port = 2222)))
            vm.state.first {
                it.config.machines
                    .single()
                    .port == 2222
            }

            vm.removeMachine("devbox")
            vm.state.first { it.config.machines.isEmpty() }
        }

    @Test
    fun worktreeSetupMovesWithItsRepository() =
        runTest {
            val vm = backgroundScope.settings()
            vm.state.first { it.snapshot != null }
            assertNull(vm.saveWorktreeSetup("/src/a", "npm ci"))
            vm.state.first { it.config.worktreeSetup == mapOf("/src/a" to "npm ci") }
            assertNull(vm.saveWorktreeSetup("/src/b", "make", previousRepo = "/src/a"))
            vm.state.first { it.config.worktreeSetup == mapOf("/src/b" to "make") }
        }

    @Test
    fun aBrokenFileIsShownAndOnlyResetReplacesIt() =
        runTest {
            path.writeText("{broken")
            val vm = backgroundScope.settings()
            val broken = vm.state.first { it.snapshot != null }
            assertNotNull(broken.snapshot?.warning)

            vm.setFlag(CoreFlags.USAGE, true)
            vm.state.first { it.error != null }
            assertEquals("{broken", path.readText())

            vm.reset()
            vm.state.first { it.snapshot?.warning == null }
            assertEquals("{}\n", path.readText())
        }

    @Test
    fun openingTheConfigCreatesItFirst() =
        runTest {
            val vm = backgroundScope.settings()
            vm.state.first { it.snapshot != null }
            vm.openConfig()
            vm.state.first { it.snapshot?.text != null }
            assertTrue(Files.exists(path))
        }

    @Test
    fun agentsAreProbedWhenTheirSectionOpens() =
        runTest {
            val vm = backgroundScope.settings()
            assertNull(vm.state.value.agents)
            vm.select(SettingsSection.Agents)
            val agents =
                vm.state
                    .first { it.agents != null }
                    .agents
                    .orEmpty()
            assertEquals(listOf("Missing", "Demo"), agents.map { it.displayName })
        }
}
