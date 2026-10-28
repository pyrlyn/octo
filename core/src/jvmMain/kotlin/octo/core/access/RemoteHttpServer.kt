package octo.core.access

import kotlinx.coroutines.runBlocking
import octo.core.http.LoopbackHttpServer

/**
 * [RemoteApi] over HTTP on `127.0.0.1:[port]`. Devices reach it through the user's own tunnel
 * (`ssh -L`, `tailscale serve`), which also encrypts the traffic, so there is no TLS here.
 * With [guestPage] it also serves the page a guest's link opens, at `/share`.
 * Throws [java.net.BindException] when the port is taken.
 */
class RemoteHttpServer(
    api: RemoteApi,
    port: Int,
    /** The tunnel's origin, whose pages may call the API; see [LoopbackHttpServer]. */
    publicOrigin: String? = null,
    private val guestPage: Boolean = false,
) : AutoCloseable {
    private val server =
        LoopbackHttpServer(port, MAX_BODY, publicOrigin) { request ->
            if (guestPage && request.path == Shares.SHARE_PATH) return@LoopbackHttpServer page(request.method)
            val body = request.body.takeIf { it.isNotEmpty() }?.decodeToString()
            val response =
                runBlocking {
                    api.handle(ApiRequest(request.method, request.path, request.authorization, body, request.headers))
                }
            LoopbackHttpServer.Response(
                response.status,
                response.body?.toString(),
                response.allow?.let { mapOf("Allow" to it) }.orEmpty(),
            )
        }

    /** E.g. `http://127.0.0.1:47470`. */
    val url: String get() = server.origin

    fun start(): RemoteHttpServer = apply { server.start() }

    override fun close() = server.close()

    private fun page(method: String) =
        if (method == "GET") {
            LoopbackHttpServer.Response(OK, PAGE, PAGE_HEADERS)
        } else {
            LoopbackHttpServer.Response(METHOD_NOT_ALLOWED, headers = mapOf("Allow" to "GET"))
        }

    companion object {
        private const val OK = 200
        private const val METHOD_NOT_ALLOWED = 405

        // A message's files travel in its body as base64: one 20 MB file, with room to spare.
        internal const val MAX_BODY = 32 shl 20

        private val PAGE: String by lazy {
            checkNotNull(RemoteHttpServer::class.java.getResource("share.html")) { "share.html is missing" }.readText()
        }

        // The page shows an agent's output to someone else's browser: no outside resources, no framing,
        // nothing but its own inline script and calls back to this server.
        private val PAGE_HEADERS =
            mapOf(
                "Content-Type" to "text/html; charset=utf-8",
                "Content-Security-Policy" to
                    "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; " +
                    "base-uri 'none'; form-action 'none'; frame-ancestors 'none'",
                "Referrer-Policy" to "no-referrer",
                "X-Content-Type-Options" to "nosniff",
                "Cache-Control" to "no-store",
            )
    }
}
