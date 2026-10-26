package octo.core.messaging

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import octo.core.task.TaskId
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * MCP's Streamable HTTP transport, reduced to what agents need: one POST, one JSON answer, no
 * server-sent events. It listens on loopback only and answers nothing without a task's token.
 */
class HttpMcpServer(
    private val messaging: AgentMessaging,
) : AutoCloseable {
    private val handler = McpHandler(messaging)

    // Each wait_for_idle holds its thread for minutes, so a fixed pool would let a few waits starve
    // every other call.
    private val pool: ExecutorService = Executors.newCachedThreadPool()
    private val server: HttpServer =
        HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
            createContext("/") { exchange -> exchange.use { serve(it) } }
            executor = pool
        }

    /** The URL agents are given, e.g. `http://127.0.0.1:52011/mcp`. */
    val url: String get() = "http://127.0.0.1:${server.address.port}$PATH"

    fun start(): HttpMcpServer = apply { server.start() }

    override fun close() {
        server.stop(0)
        pool.shutdownNow()
    }

    private fun serve(exchange: HttpExchange) {
        val headers = exchange.requestHeaders
        when {
            exchange.requestURI.path != PATH -> {
                reply(exchange, NOT_FOUND)
            }

            // A web page the user visits could otherwise post to a loopback port (DNS rebinding).
            !localOrigin(headers.getFirst("Origin")) -> {
                reply(exchange, FORBIDDEN)
            }

            exchange.requestMethod != "POST" -> {
                exchange.responseHeaders.add("Allow", "POST")
                reply(exchange, METHOD_NOT_ALLOWED)
            }

            else -> {
                when (val caller = messaging.caller(headers.getFirst("Authorization"))) {
                    null -> reply(exchange, UNAUTHORIZED)
                    else -> answer(exchange, caller)
                }
            }
        }
    }

    private fun answer(
        exchange: HttpExchange,
        caller: TaskId,
    ) {
        val body = exchange.requestBody.readNBytes(MAX_BODY + 1)
        val request = if (body.size > MAX_BODY) null else parse(body)
        when {
            body.size > MAX_BODY -> {
                reply(exchange, TOO_LARGE)
            }

            request == null -> {
                respond(exchange, McpHandler.error(JsonNull, McpHandler.PARSE_ERROR, "Not JSON"))
            }

            else -> {
                val response = runBlocking { handler.handle(caller, request) }
                if (response == null) reply(exchange, ACCEPTED) else respond(exchange, response)
            }
        }
    }

    private fun parse(body: ByteArray): JsonElement? =
        try {
            Json.parseToJsonElement(body.decodeToString())
        } catch (ignored: SerializationException) {
            null
        }

    private fun respond(
        exchange: HttpExchange,
        response: JsonElement,
    ) {
        val bytes = response.toString().encodeToByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        try {
            exchange.sendResponseHeaders(OK, bytes.size.toLong())
            exchange.responseBody.write(bytes)
        } catch (ignored: IOException) {
            // The agent hung up (it gave up on a long wait); there is nobody left to answer.
        }
    }

    private fun reply(
        exchange: HttpExchange,
        code: Int,
    ) {
        try {
            exchange.sendResponseHeaders(code, -1)
        } catch (ignored: IOException) {
            // As in respond: the client is gone.
        }
    }

    private companion object {
        const val PATH = "/mcp"
        const val MAX_BODY = 1 shl 20
        const val OK = 200
        const val ACCEPTED = 202
        const val UNAUTHORIZED = 401
        const val FORBIDDEN = 403
        const val NOT_FOUND = 404
        const val METHOD_NOT_ALLOWED = 405
        const val TOO_LARGE = 413

        fun localOrigin(origin: String?): Boolean {
            if (origin == null) return true
            val host =
                try {
                    URI(origin).host
                } catch (ignored: java.net.URISyntaxException) {
                    null
                }
            return host == "localhost" || host == "127.0.0.1" || host == "[::1]"
        }
    }
}
