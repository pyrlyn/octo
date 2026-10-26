package octo.core.paths

import kotlinx.io.files.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OctoPathsTest {
    @Test
    fun octoHomeWins() {
        assertEquals(Path("/x/octo"), OctoPaths.resolve(mapOf("OCTO_HOME" to "/x/octo"), HostOs.MacOs, "/u").home)
    }

    @Test
    fun platformDefaults() {
        assertEquals(
            Path("/u", "Library", "Application Support", "Octo"),
            OctoPaths.resolve(emptyMap(), HostOs.MacOs, "/u").home,
        )
        assertEquals(Path("/u", ".local", "state", "octo"), OctoPaths.resolve(emptyMap(), HostOs.Linux, "/u").home)
        assertEquals(Path("/s", "octo"), OctoPaths.resolve(mapOf("XDG_STATE_HOME" to "/s"), HostOs.Linux, "/u").home)
        assertEquals(Path("/a", "Octo"), OctoPaths.resolve(mapOf("APPDATA" to "/a"), HostOs.Windows, "/u").home)
    }

    @Test
    fun socketsLiveInThePrivateTempDirectoryNotTheHome() {
        val mac = mapOf("TMPDIR" to "/var/folders/ab/T", "OCTO_HOME" to "/x/octo")
        assertEquals(Path("/var/folders/ab/T", "octo"), OctoPaths.resolve(mac, HostOs.MacOs, "/u").sockets)
        val linux = mapOf("XDG_RUNTIME_DIR" to "/run/user/1000")
        assertEquals(Path("/run/user/1000", "octo"), OctoPaths.resolve(linux, HostOs.Linux, "/u").sockets)
        assertNull(OctoPaths.resolve(emptyMap(), HostOs.Linux, "/u").sockets)
        assertNull(OctoPaths.resolve(mapOf("TMPDIR" to "relative"), HostOs.MacOs, "/u").sockets)
        assertNull(OctoPaths.resolve(mapOf("TMPDIR" to "/t"), HostOs.Windows, "/u").sockets)
    }
}
