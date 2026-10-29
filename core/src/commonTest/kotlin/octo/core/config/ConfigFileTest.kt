package octo.core.config

import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import octo.core.agent.PermissionMode
import octo.core.io.AtomicFile
import octo.core.tempDir
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigFileTest {
    private val atomic = AtomicFile(Path(tempDir("octo-config"), "config.json"))
    private val file = ConfigFile(atomic)

    @Test
    fun editsSetTheirOwnKeyAndKeepTheRest() =
        runTest {
            atomic.write("""{"flags":{"usage":true},"future":{"keep":1}}""")

            file.update(ConfigEdit.flag("fork", on = true))
            file.update(ConfigEdit.defaultPermissionMode(PermissionMode.AutoAcceptEdits))
            file.update(ConfigEdit.worktreeSetup("/src/rtok", "cargo fetch"))
            file.update(ConfigEdit.remotePort(47471))
            val last = file.update(ConfigEdit.appearance(AppearanceConfig(theme = AppearanceTheme.Dark)))

            val config = last.config
            assertEquals(mapOf("usage" to true, "fork" to true), config.flags)
            assertEquals(PermissionMode.AutoAcceptEdits, config.defaultPermissionMode)
            assertEquals(mapOf("/src/rtok" to "cargo fetch"), config.worktreeSetup)
            assertEquals(47471, config.remoteAccess.port)
            assertEquals(AppearanceTheme.Dark, config.appearance.theme)
            assertTrue("\"future\"" in last.text.orEmpty(), "an unknown key survives")
            assertNull(last.warning)

            val removed = file.update(ConfigEdit.worktreeSetup("/src/rtok", null))
            assertEquals(emptyMap(), removed.config.worktreeSetup)
        }

    @Test
    fun machinesAreAddedReplacedAndRemovedById() =
        runTest {
            file.update(ConfigEdit.machine(null, MachineConfig("box", "box.lan")))
            file.update(ConfigEdit.machine(null, MachineConfig("gpu", "gpu.lan")))
            file.update(ConfigEdit.machine("box", MachineConfig("build-box", "build.lan", port = 2222)))
            val after = file.update(ConfigEdit.machine("gpu", null))

            assertEquals(listOf(MachineConfig("build-box", "build.lan", port = 2222)), after.config.machines)
        }

    @Test
    fun aBrokenFileIsReportedAndNeverOverwritten() =
        runTest {
            atomic.write("{\"flags\": ")

            val loaded = file.load()
            assertTrue(loaded.warning.orEmpty().startsWith("Ignoring invalid config.json"))
            assertFailsWith<InvalidConfigException> { file.update(ConfigEdit.flag("fork", on = true)) }
            assertEquals("{\"flags\": ", atomic.read())

            val reset = file.reset()
            assertEquals(OctoConfig(), reset.config)
            assertNull(reset.warning)
        }

    @Test
    fun problemsUseOctosOwnWords() =
        runTest {
            atomic.write("""{"remoteAccess":{"port":70000},"machines":[{"id":"Build_Box","host":"build.lan"}]}""")

            assertEquals(
                listOf(
                    "remoteAccess.port 70000 is not a TCP port",
                    "Machine id 'Build_Box' must be lowercase letters, digits and dashes",
                ),
                file.load().problems,
            )
        }
}
