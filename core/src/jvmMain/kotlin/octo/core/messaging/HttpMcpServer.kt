package octo.core.messaging

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import octo.core.http.LoopbackHttpServer
import octo.core.http.LoopbackHttpServer.Request
import octo.core.http.LoopbackHttpServer.Response
import octo.core.task.TaskId

/**
 * MCP's Streamable HTTP transport, reduced to what agents need: one POST, one JSON answer, no
 * server-sent events. It listens on loopback only and answers nothing without a task's token.
 */
class HttpMcpServer(
    private val messaging: AgentMessaging,
) : AutoCloseable {
    private val handler = McpHandler(messaging)
    private val server = LoopbackHttpServer { serve(it) }

    /** The URL agents are given, e.g. `http://127.0.0.1:52011/mcp`. */
    val url: String get() = server.origin + PATH

    fun start(): HttpMcpServer = apply { server.start() }

    override fun close() = server.close()

    private fun serve(request: Request): Response =
        when {
            request.path != PATH -> {
                Response(NOT_FOUND)
            }

            request.method != "POST" -> {
                Response(METHOD_NOT_ALLOWED, headers = mapOf("Allow" to "POST"))
            }

            else -> {
                when (val caller = messaging.caller(request.authorization)) {
                    null -> Response(UNAUTHORIZED)
                    else -> answer(request.body, caller)
                }
            }
        }

    private fun answer(
        body: ByteArray,
        caller: TaskId,
    ): Response {
        val request = parse(body) ?: return json(McpHandler.error(JsonNull, McpHandler.PARSE_ERROR, "Not JSON"))
        val response = runBlocking { handler.handle(caller, request) }
        return if (response == null) Response(ACCEPTED) else json(response)
    }

    private fun parse(body: ByteArray): JsonElement? =
        try {
            Json.parseToJsonElement(body.decodeToString())
        } catch (ignored: SerializationException) {
            null
        }

    private fun json(response: JsonElement) = Response(OK, response.toString())

    private companion object {
        const val PATH = "/mcp"
        const val OK = 200
        const val ACCEPTED = 202
        const val UNAUTHORIZED = 401
        const val NOT_FOUND = 404
        const val METHOD_NOT_ALLOWED = 405
    }
}
