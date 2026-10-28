package octo.core.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RemoteAccessConfigTest {
    @Test
    fun aPublicUrlIsAnOriginOnly() {
        assertNull(RemoteAccessConfig().problem())
        val tunnel = RemoteAccessConfig(publicUrl = "https://box.tailnet.ts.net/")
        assertNull(tunnel.problem())
        assertEquals("https://box.tailnet.ts.net", tunnel.publicOrigin)
        assertNull(RemoteAccessConfig(publicUrl = "http://localhost:8080").problem())
        assertNotNull(RemoteAccessConfig(publicUrl = "box.tailnet.ts.net").problem())
        assertNotNull(RemoteAccessConfig(publicUrl = "https://box/octo").problem())
        assertNotNull(RemoteAccessConfig(publicUrl = "javascript:alert(1)").problem())
        assertEquals(
            "https://box",
            OctoConfig
                .parse("""{"remoteAccess":{"publicUrl":"https://box"}}""")
                .config.remoteAccess.publicOrigin,
        )
    }
}
