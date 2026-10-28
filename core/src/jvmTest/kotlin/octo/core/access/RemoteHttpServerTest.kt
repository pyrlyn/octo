package octo.core.access

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import octo.core.agent.AdapterRegistry
import octo.core.flags.FeatureFlags
import octo.core.io.AtomicFile
import octo.core.orchestrator.FakeAdapter
import octo.core.orchestrator.FakeWorkspace
import octo.core.orchestrator.Orchestrator
import octo.core.persist.StateStore
import octo.core.tempDir
import octo.core.terms.TermsGate
import octo.core.triggers.Delivery
import octo.core.triggers.Webhooks
import java.net.BindException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The JDK's HTTP client plays the phone; the server is real and bound to loopback. */
class RemoteHttpServerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val home = tempDir("octo-remote")
    private val devices = runBlocking { Devices.open(AtomicFile(Path(home, "devices.json")), JvmSecrets) }
    private val core =
        runBlocking {
            Orchestrator.open(
                AdapterRegistry(listOf(FakeAdapter()), FeatureFlags()),
                FakeWorkspace(),
                StateStore(Path(home, "state.json")),
                TermsGate(AtomicFile(Path(home, "acknowledgements.json"))),
                Path(home, "worktrees"),
                scope,
            )
        }
    private val delivered = mutableListOf<Map<String, String>>()
    private val hooks =
        Webhooks { id, headers, _ ->
            delivered += headers
            if (id == "pr") Delivery.Accepted else Delivery.NotFound
        }
    private val server = RemoteHttpServer(RemoteApi(core, devices, hooks), 0).start()
    private val client = HttpClient.newHttpClient()

    @AfterTest
    fun close() {
        server.close()
        scope.cancel()
    }

    private fun send(
        method: String,
        path: String,
        body: String? = null,
        token: String? = null,
        origin: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): HttpResponse<String> {
        val request =
            HttpRequest
                .newBuilder(URI(server.url + path))
                .method(
                    method,
                    body?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody(),
                )
        token?.let { request.header("Authorization", "Bearer $it") }
        origin?.let { request.header("Origin", it) }
        headers.forEach { (name, value) -> request.header(name, value) }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun pair(): String {
        val code = runBlocking { devices.startPairing() }.code
        val response = send("POST", "/api/v1/pair", """{"code":"$code","name":"Phone"}""")
        assertEquals(200, response.statusCode())
        return Regex("\"token\":\"([0-9a-f]+)\"").find(response.body())!!.groupValues[1]
    }

    @Test
    fun aPairedDeviceTalksJsonOverLoopback() {
        assertTrue(server.url.startsWith("http://127.0.0.1:"))
        val token = pair()
        assertEquals(64, token.length)
        assertFalse(
            token in
                devices.devices.value
                    .single()
                    .tokenHash,
        )

        val tasks = send("GET", "/api/v1/tasks", token = token)
        assertEquals(200, tasks.statusCode())
        assertEquals("application/json", tasks.headers().firstValue("Content-Type").orElse(null))
        assertEquals("""{"tasks":[]}""", tasks.body())
        assertEquals(404, send("POST", "/api/v1/tasks/nope/stop", token = token).statusCode())
        val wrongMethod = send("PUT", "/api/v1/tasks", "{}", token = token)
        assertEquals(405, wrongMethod.statusCode())
        assertEquals("GET, POST", wrongMethod.headers().firstValue("Allow").orElse(null))
    }

    @Test
    fun strangersAndWebPagesAreRefused() {
        val token = pair()
        assertEquals(401, send("GET", "/api/v1/tasks").statusCode())
        assertEquals(401, send("GET", "/api/v1/tasks", token = "0".repeat(64)).statusCode())
        assertEquals(403, send("GET", "/api/v1/tasks", token = token, origin = "https://evil.example").statusCode())
        assertEquals(
            413,
            send("POST", "/api/v1/tasks", "x".repeat(RemoteHttpServer.MAX_BODY + 1), token = token).statusCode(),
        )
    }

    @Test
    fun aTakenPortIsReported() {
        val port = server.url.substringAfterLast(':').toInt()
        assertFailsWith<BindException> { RemoteHttpServer(RemoteApi(core, devices), port) }
    }

    @Test
    fun secretsUseRealSha256() {
        // The standard test vector for "abc".
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            JvmSecrets.sha256("abc".encodeToByteArray()).toHexString(),
        )
        assertEquals(32, JvmSecrets.randomBytes(32).size)
        // RFC 4231, test case 2.
        assertEquals(
            "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843",
            JvmSecrets
                .hmacSha256(
                    "Jefe".encodeToByteArray(),
                    "what do ya want for nothing?".encodeToByteArray(),
                ).toHexString(),
        )
    }

    @Test
    fun aWebhookGetsItsHeadersWithoutADeviceToken() {
        val accepted = send("POST", "/api/v1/hooks/pr", "{}", headers = mapOf("X-GitHub-Event" to "push"))
        assertEquals(202, accepted.statusCode())
        assertEquals("push", delivered.single()["x-github-event"])
        assertEquals(404, send("POST", "/api/v1/hooks/other", "{}").statusCode())
        assertEquals(403, send("POST", "/api/v1/hooks/pr", "{}", origin = "https://evil.example").statusCode())
    }

    @Test
    fun theGuestPageIsServedLockedDownAndItsOriginMayCall() {
        assertEquals(404, send("GET", "/share").statusCode(), "without guest-share there is no page")
        val shares = runBlocking { Shares.open(AtomicFile(Path(home, "shares.json")), JvmSecrets, core) }
        val tunnel = "https://box.tailnet.ts.net"
        RemoteHttpServer(RemoteApi(core, devices, shares = shares), 0, tunnel, guestPage = true).start().use { guests ->
            fun send(
                method: String,
                path: String,
                origin: String? = null,
            ): HttpResponse<String> {
                val request =
                    HttpRequest
                        .newBuilder(
                            URI(guests.url + path),
                        ).method(method, HttpRequest.BodyPublishers.noBody())
                origin?.let { request.header("Origin", it) }
                return client.send(request.build(), HttpResponse.BodyHandlers.ofString())
            }

            val page = send("GET", Shares.SHARE_PATH)
            assertEquals(200, page.statusCode())
            assertEquals("text/html; charset=utf-8", page.headers().firstValue("Content-Type").orElse(null))
            assertTrue("default-src 'none'" in page.headers().firstValue("Content-Security-Policy").orElse(""))
            assertTrue("location.hash" in page.body())
            assertEquals(405, send("POST", Shares.SHARE_PATH).statusCode())
            assertEquals(
                401,
                send("GET", "/api/v1/guest", tunnel).statusCode(),
                "the tunnel's origin gets past the check",
            )
            assertEquals(403, send("GET", "/api/v1/guest", "https://evil.example").statusCode())
        }
    }
}
