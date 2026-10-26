package octo.core.messaging

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import octo.core.task.TaskId
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The JDK's HTTP client plays the agent; the server is real and bound to loopback. */
class HttpMcpServerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val messaging = AgentMessaging(scope) { "secret" }
    private val server = HttpMcpServer(messaging).start()
    private val client = HttpClient.newHttpClient()

    init {
        messaging.bind(server.url)
        messaging.servers(TaskId("t1"))
    }

    @AfterTest
    fun close() {
        server.close()
        scope.cancel()
    }

    private fun post(
        body: String,
        token: String? = "secret",
        origin: String? = null,
        path: String = "/mcp",
    ): HttpResponse<String> {
        val request =
            HttpRequest
                .newBuilder(URI(server.url.removeSuffix("/mcp") + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
        token?.let { request.header("Authorization", "Bearer $it") }
        origin?.let { request.header("Origin", it) }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString())
    }

    private val initialize =
        """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25"}}"""

    @Test
    fun listensOnLoopback() {
        assertTrue(server.url.startsWith("http://127.0.0.1:"))
        assertTrue(server.url.endsWith("/mcp"))
    }

    @Test
    fun answersAnAgentWithItsToken() {
        val response = post(initialize)
        assertEquals(200, response.statusCode())
        assertEquals("application/json", response.headers().firstValue("Content-Type").orElse(null))
        assertTrue("\"protocolVersion\":\"2025-11-25\"" in response.body())
        assertEquals(202, post("""{"jsonrpc":"2.0","method":"notifications/initialized"}""").statusCode())
        assertTrue("-32700" in post("not json").body())
    }

    @Test
    fun refusesEveryoneElse() {
        assertEquals(401, post(initialize, token = null).statusCode())
        assertEquals(401, post(initialize, token = "guess").statusCode())
        assertEquals(403, post(initialize, origin = "https://evil.example").statusCode())
        assertEquals(200, post(initialize, origin = "http://localhost:3000").statusCode())
        assertEquals(404, post(initialize, path = "/other").statusCode())
        assertEquals(413, post("x".repeat((1 shl 20) + 1)).statusCode())

        val get =
            HttpRequest
                .newBuilder(URI(server.url))
                .header("Authorization", "Bearer secret")
                .GET()
                .build()
        assertEquals(405, client.send(get, HttpResponse.BodyHandlers.ofString()).statusCode())
    }
}
