package octo.core.plugins

import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.writeString
import octo.core.flags.CoreFlags
import octo.core.flags.FeatureFlags
import octo.core.status.AgentStatus
import octo.core.tempDir
import wasm.HostFunction
import wasm.WasmInstance
import wasm.WasmRuntime
import wasm.chasm.ChasmRuntime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// The modules are built from fixtures/plugins/demo-plugin by fixtures/plugins/build.sh.
class PluginLoaderTest {
    private val dir = tempDir("octo-plugins")
    private val on = FeatureFlags(mapOf(CoreFlags.PLUGINS.id to true))
    private val task = PluginTask("Fix login", "/repo", "octo/fix-login", "main", "claude", AgentStatus.Idle)

    private fun install(
        fixture: String,
        name: String = fixture,
    ) {
        val bytes = SystemFileSystem.source(Path(FIXTURES, fixture)).buffered().use { it.readByteArray() }
        SystemFileSystem.sink(Path(dir, name)).buffered().use { it.write(bytes) }
    }

    private fun write(
        name: String,
        text: String,
    ) = SystemFileSystem.sink(Path(dir, name)).buffered().use { it.writeString(text) }

    @Test
    fun theFlagOffReadsNothing() =
        runTest {
            write("broken.wasm", "not wasm")
            val untouched =
                object : WasmRuntime {
                    override fun instantiate(
                        module: ByteArray,
                        imports: List<HostFunction>,
                        fuel: Long?,
                    ): WasmInstance = throw AssertionError("a plugin was loaded with the flag off")
                }

            val loaded = PluginLoader.load(FeatureFlags(emptyMap()), dir, untouched)

            assertEquals(LoadedPlugins(), loaded)
        }

    @Test
    fun loadsAPluginAndRunsItsActions() =
        runTest {
            install("demo.wasm")

            val loaded = PluginLoader.load(on, dir, ChasmRuntime)

            assertEquals(emptyList(), loaded.warnings)
            val plugin = loaded.plugins.single()
            assertEquals("demo", plugin.manifest.id)
            assertEquals(listOf("review", "note"), plugin.manifest.actions.map { it.id })
            val agent = plugin.manifest.agents.single()
            assertEquals(PluginAgent("demo-acp", "Demo ACP", "demo-acp", listOf("--acp"), "1.0.0"), agent)
            assertEquals(PluginResult("Review the changes on octo/fix-login.", null), plugin.run("review", task))
            assertEquals(PluginResult(null, "Hello from the demo plugin about Fix login."), plugin.run("note", task))
        }

    @Test
    fun anActionTheManifestDoesNotListIsRefused() =
        runTest {
            install("demo.wasm")
            val plugin = PluginLoader.load(on, dir, ChasmRuntime).plugins.single()

            assertFailsWith<IllegalArgumentException> { plugin.run("deploy", task) }
        }

    @Test
    fun brokenPluginsAreWarnedAboutAndSkipped() =
        runTest {
            install("demo.wasm", "a-demo.wasm")
            install("demo.wasm", "b-demo.wasm")
            install("future-abi.wasm")
            write("not-wasm.wasm", "#!/bin/sh\necho hi\n")
            write("notes.txt", "ignored, not a .wasm file")

            val loaded = PluginLoader.load(on, dir, ChasmRuntime)

            assertEquals(listOf("a-demo.wasm"), loaded.plugins.map { it.file })
            val warnings = loaded.warnings
            assertEquals(3, warnings.size, warnings.toString())
            assertTrue(warnings[0].startsWith("Skipping plugin b-demo.wasm: a-demo.wasm already has the id 'demo'"))
            assertTrue(warnings[1].startsWith("Skipping plugin future-abi.wasm: it needs plugin ABI 99"), warnings[1])
            assertTrue(warnings[2].startsWith("Skipping plugin not-wasm.wasm: "), warnings[2])
        }

    @Test
    fun fuelStopsAPluginThatNeverReturns() =
        runTest {
            install("spin.wasm")
            val plugin = PluginLoader.load(on, dir, ChasmRuntime, fuel = 1_000_000).plugins.single()

            assertFailsWith<PluginException> { plugin.run("review", task) }
        }

    @Test
    fun noPluginsFolderMeansNoPlugins() =
        runTest {
            assertEquals(LoadedPlugins(), PluginLoader.load(on, Path(dir, "missing"), ChasmRuntime))
        }

    @Test
    fun manifestFieldsAreChecked() {
        val agent = PluginAgent("x", "X", "x-agent", emptyList(), "1.0")

        fun manifest(vararg agents: PluginAgent) = PluginManifest(1, "p", "P", "1", agents.toList())

        assertEquals(null, manifest(agent).problem())
        assertTrue(manifest(agent.copy(command = "/bin/sh")).problem()!!.contains("bare program name"))
        assertTrue(manifest(agent.copy(command = "-rf")).problem()!!.contains("bare program name"))
        assertTrue(manifest(agent.copy(args = listOf("a\nb"))).problem()!!.contains("arguments"))
        assertTrue(manifest(agent, agent).problem()!!.contains("share an id"))
        assertTrue(PluginManifest(1, "Bad Id", "P", "1").problem()!!.contains("lowercase"))
    }

    private companion object {
        const val FIXTURES = "src/commonTest/fixtures/plugins"
    }
}
