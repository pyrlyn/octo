package octo.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import octo.core.flags.CoreFlags
import octo.core.paths.OctoPaths
import octo.core.process.JvmProcessLauncher
import java.io.File
import java.net.ConnectException
import java.net.URI
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun aBrokenConfigStartsWithDefaultsAndAWarning() {
        val octo = open("{not json")
        assertNull(octo.publisher)
        assertEquals(1, octo.warnings.size)
        assertTrue(octo.warnings.single().contains("config.json"))
    }
}
