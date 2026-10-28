package octo.core.http

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.net.URISyntaxException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * An HTTP server bound to `127.0.0.1` only. It refuses requests from web pages on other origins
 * and bodies over [maxBody] before [handle] sees them; everything else is [handle]'s to decide.
 * Throws [java.net.BindException] when [port] is taken.
 */
class LoopbackHttpServer(
    port: Int = 0,
    private val maxBody: Int = DEFAULT_MAX_BODY,
    private val handle: (Request) -> Response,
) : AutoCloseable {
    class Request(
        val method: String,
        val path: String,
        val authorization: String?,
        val body: ByteArray,
        /** Lowercase names, first values. */
        val headers: Map<String, String> = emptyMap(),
    )

    /** A null [body] sends no content; a non-null one is sent as JSON. */
    class Response(
        val status: Int,
        val body: String? = null,
        val headers: Map<String, String> = emptyMap(),
    )

    // A handler may hold its thread for minutes (an agent waiting on another), so a fixed pool
    // would let a few slow calls starve every other one.
    private val pool: ExecutorService = Executors.newCachedThreadPool()
    private val server: HttpServer =
        HttpServer.create(InetSocketAddress(InetAddress.getByName(HOST), port), 0).apply {
            createContext("/") { exchange -> exchange.use { serve(it) } }
            executor = pool
        }

    val port: Int get() = server.address.port

    /** E.g. `http://127.0.0.1:52011`. */
    val origin: String get() = "http://$HOST:$port"

    fun start(): LoopbackHttpServer = apply { server.start() }

    override fun close() {
        server.stop(0)
        pool.shutdownNow()
    }

    private fun serve(exchange: HttpExchange) {
        val headers = exchange.requestHeaders
        // A web page the user visits could otherwise post to a loopback port (DNS rebinding).
        if (!localOrigin(headers.getFirst("Origin"))) return send(exchange, Response(FORBIDDEN))
        val body = exchange.requestBody.readNBytes(maxBody + 1)
        val response =
            if (body.size > maxBody) {
                Response(TOO_LARGE)
            } else {
                val first = headers.entries.associate { (name, values) -> name.lowercase() to values.first() }
                handle(
                    Request(
                        exchange.requestMethod,
                        exchange.requestURI.path,
                        headers.getFirst("Authorization"),
                        body,
                        first,
                    ),
                )
            }
        send(exchange, response)
    }

    private fun send(
        exchange: HttpExchange,
        response: Response,
    ) {
        response.headers.forEach { (name, value) -> exchange.responseHeaders.add(name, value) }
        val bytes = response.body?.encodeToByteArray()
        try {
            if (bytes == null) {
                exchange.sendResponseHeaders(response.status, -1)
            } else {
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(response.status, bytes.size.toLong())
                exchange.responseBody.write(bytes)
            }
        } catch (ignored: IOException) {
            // The client hung up (an agent gave up on a long wait); there is nobody left to answer.
        }
    }

    private companion object {
        const val HOST = "127.0.0.1"
        const val DEFAULT_MAX_BODY = 1 shl 20
        const val FORBIDDEN = 403
        const val TOO_LARGE = 413

        fun localOrigin(origin: String?): Boolean {
            if (origin == null) return true
            val host =
                try {
                    URI(origin).host
                } catch (ignored: URISyntaxException) {
                    null
                }
            return host == "localhost" || host == HOST || host == "[::1]"
        }
    }
}
