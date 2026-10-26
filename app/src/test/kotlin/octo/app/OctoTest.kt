package octo.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import octo.core.agent.AgentId
import octo.core.flags.CoreFlags
import octo.core.paths.OctoPaths
import octo.core.process.JvmProcessLauncher
import java.io.File
import java.net.ConnectException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OctoTest {
    private val home = Files.createTempDirectory("octo-app").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun cleanUp() {
        scope.cancel()
        home.deleteRecursively()
    }

    private fun open(config: String?) =
        runBlocking {
            config?.let { File(home, "config.json").writeText(it) }
            Octo.open(OctoPaths(Path(home.path)), JvmProcessLauncher(emptyMap()), scope).also { it.close() }
        }

    @Test
    fun publishingNeedsItsFlag() {
        assertNull(open(null).publisher)
        assertNotNull(open("""{"flags": {"${CoreFlags.GIT_PUBLISH.id}": true}}""").publisher)
    }

    @Test
    fun remoteMachinesNeedTheirFlagAndBadEntriesAreSkipped() {
        val machines = """"machines": [{"id": "box", "host": "me@box"}, {"id": "bad", "host": "-oProxyCommand=id"}]"""
        val off = open("{$machines}")
        assertEquals(emptyList(), off.catalog.machines)
        assertEquals(emptyList(), off.warnings, "nothing is checked while the feature is off")

        val on = open("""{"flags": {"${CoreFlags.REMOTE_SSH.id}": true}, $machines}""")
        assertEquals(listOf("box"), on.catalog.machines)
        assertTrue(on.warnings.single().contains("'bad'"), on.warnings.toString())
    }

    @Test
    fun agentMessagingNeedsItsFlagAndStopsWithOcto() {
        assertNull(open(null).mcpServer)
        val url = assertNotNull(open("""{"flags": {"${CoreFlags.AGENT_MESSAGING.id}": true}}""").mcpServer).url
        assertTrue(url.startsWith("http://127.0.0.1:"))
        assertFailsWith<ConnectException>(
            "closing Octo stops the server",
        ) { URI(url).toURL().openConnection().connect() }
    }

    @Test
    fun presetsNeedTheirFlag() {
        File(home, "presets.json").writeText("{broken")
        val off = open(null)
        assertNull(off.presets)
        assertEquals(emptyList(), off.warnings, "the presets file is not read while the feature is off")

        val on = open("""{"flags": {"${CoreFlags.PRESETS.id}": true}}""")
        assertNotNull(on.presets)
        assertTrue(on.warnings.single().contains("presets.json"), on.warnings.toString())
    }

    @Test
    fun remoteAccessNeedsItsFlagListensOnLoopbackAndStopsWithOcto() {
        val port = ServerSocket(0).use { it.localPort }
        val config = """{"flags": {"${CoreFlags.REMOTE_ACCESS.id}": true}, "remoteAccess": {"port": $port}}"""
        val off = open("""{"remoteAccess": {"port": $port}}""")
        assertNull(off.remoteUrl)
        assertNull(off.devices)
        assertFalse(File(home, "devices.json").exists(), "nothing is read or written while the feature is off")

        val on = open(config)
        assertEquals("http://127.0.0.1:$port", on.remoteUrl)
        assertNotNull(on.devices)
        assertFailsWith<ConnectException>("closing Octo stops the server") {
            URI(on.remoteUrl!!).toURL().openConnection().connect()
        }
    }

    @Test
    fun aTakenPortOrABadOneTurnsRemoteAccessOffWithAWarning() {
        ServerSocket(0, 0, InetAddress.getByName("127.0.0.1")).use { taken ->
            val flag = """"flags": {"${CoreFlags.REMOTE_ACCESS.id}": true}"""
            val busy = open("""{$flag, "remoteAccess": {"port": ${taken.localPort}}}""")
            assertNull(busy.remoteUrl)
            assertNull(busy.devices)
            assertTrue(busy.warnings.single().contains("Remote access is off"), busy.warnings.toString())

            val bad = open("""{$flag, "remoteAccess": {"port": 70000}}""")
            assertNull(bad.remoteUrl)
            assertTrue(bad.warnings.single().contains("70000"), bad.warnings.toString())
        }
    }

    @Test
    fun pluginsNeedTheirFlagAndAddTheirAgents() {
        val plugins = File(home, "plugins").apply { mkdirs() }
        File(DEMO_PLUGIN).copyTo(File(plugins, "demo.wasm"))
        File(plugins, "broken.wasm").writeText("not wasm")
        val off = open(null)
        assertEquals(emptyList(), off.plugins)
        assertEquals(emptyList(), off.warnings, "the plugins folder is not read while the feature is off")
        assertFalse(runBlocking { off.catalog.agents() }.any { it.id == DEMO_AGENT })

        val on = open("""{"flags": {"${CoreFlags.PLUGINS.id}": true}}""")
        assertEquals(listOf("demo"), on.plugins.map { it.manifest.id })
        assertTrue(on.warnings.single().contains("broken.wasm"), on.warnings.toString())
        assertTrue(runBlocking { on.catalog.agents() }.any { it.id == DEMO_AGENT })
    }

    @Test
    fun aBrokenConfigStartsWithDefaultsAndAWarning() {
        val octo = open("{not json")
        assertNull(octo.publisher)
        assertEquals(1, octo.warnings.size)
        assertTrue(octo.warnings.single().contains("config.json"))
    }

    private companion object {
        // Built by core/src/commonTest/fixtures/plugins/build.sh.
        const val DEMO_PLUGIN = "../core/src/commonTest/fixtures/plugins/demo.wasm"
        val DEMO_AGENT = AgentId("demo-acp")
    }
}
