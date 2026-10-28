package octo.core.access

import kotlinx.coroutines.runBlocking
import octo.core.http.LoopbackHttpServer

/**
 * [RemoteApi] over HTTP on `127.0.0.1:[port]`. Devices reach it through the user's own tunnel
 * (`ssh -L`, `tailscale serve`), which also encrypts the traffic, so there is no TLS here.
 * Throws [java.net.BindException] when the port is taken.
 */
class RemoteHttpServer(
    api: RemoteApi,
    port: Int,
) : AutoCloseable {
    private val server =
        LoopbackHttpServer(port) { request ->
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
}
