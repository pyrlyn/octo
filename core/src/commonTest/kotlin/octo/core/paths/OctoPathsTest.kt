package octo.core.paths

import kotlinx.io.files.Path
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
