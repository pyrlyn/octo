package octo.adapters.base

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.coroutines.cancellation.CancellationException

class JsonRpcException(
    val code: Int,
    message: String,
    val data: JsonElement? = null,
) : Exception(message) {
    companion object {
        const val PARSE_ERROR = -32700
        const val METHOD_NOT_FOUND = -32601
        const val INVALID_PARAMS = -32602
        const val INTERNAL_ERROR = -32603
    }
}

/** What the other side may call on us. */
interface JsonRpcHandler {
    /** Answers a request; throw [JsonRpcException] to reply with an error. May suspend for user input. */
    suspend fun onRequest(
        method: String,
        params: JsonElement?,
    ): JsonElement

    suspend fun onNotification(
        method: String,
        params: JsonElement?,
    )
}

/**
 * A JSON-RPC 2.0 peer over newline-framed text. Incoming requests run concurrently, because they
 * may wait for the user; notifications are handled in arrival order.
 */
class JsonRpcPeer(
    private val scope: CoroutineScope,
    private val send: suspend (String) -> Unit,
    private val handler: JsonRpcHandler,
    /** Codex `app-server` omits the `jsonrpc` field, so it can be left out. */
    private val withVersionField: Boolean = true,
    /** Lines that are not JSON-RPC and handler failures; reported, never fatal. */
    private val onProtocolIssue: (String) -> Unit = {},
) {
    private val lock = Mutex()
    private val pending = mutableMapOf<String, CompletableDeferred<JsonElement>>()
    private var nextId = 1L
    private var closed = false

    suspend fun request(
        method: String,
        params: JsonElement? = null,
    ): JsonElement {
        val reply = CompletableDeferred<JsonElement>()
        val id =
            lock.withLock {
                if (closed) throw JsonRpcException(JsonRpcException.INTERNAL_ERROR, "Connection closed")
                val id = nextId++
                pending[JsonPrimitive(id).toString()] = reply
                id
            }
        send(
            message {
                put("id", id)
                put("method", method)
                params?.let { put("params", it) }
            },
        )
        return reply.await()
    }

    suspend fun notify(
        method: String,
        params: JsonElement? = null,
    ) {
        send(
            message {
                put("method", method)
                params?.let { put("params", it) }
            },
        )
    }

    /** Reads [incoming] until it ends; requests still waiting then fail. */
    suspend fun run(incoming: Flow<String>) {
        try {
            incoming.collect { line -> if (line.isNotBlank()) dispatch(line) }
        } finally {
            val waiting =
                lock.withLock {
                    closed = true
                    pending.values.toList().also { pending.clear() }
                }
            waiting.forEach {
                it.completeExceptionally(
                    JsonRpcException(JsonRpcException.INTERNAL_ERROR, "Connection closed"),
                )
            }
        }
    }

    private suspend fun dispatch(line: String) {
        val message =
            try {
                Json.parseToJsonElement(line) as? JsonObject
            } catch (_: SerializationException) {
                null
            }
        if (message == null) {
            onProtocolIssue(line)
            return
        }
        val id = message["id"]?.takeUnless { it is JsonNull }
        val method = (message["method"] as? JsonPrimitive)?.contentOrNull
        when {
            method != null && id != null -> scope.launch { answer(id, method, message["params"]) }
            method != null -> notification(method, message["params"])
            id != null -> resolve(id, message)
            else -> onProtocolIssue(line)
        }
    }

    // Handler failures are turned into protocol errors or log lines: a bug in one adapter must not
    // take the connection down.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun answer(
        id: JsonElement,
        method: String,
        params: JsonElement?,
    ) {
        val reply =
            try {
                val result = handler.onRequest(method, params)
                message {
                    put("id", id)
                    put("result", result)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: JsonRpcException) {
                errorReply(id, e.code, e.message, e.data)
            } catch (e: Exception) {
                onProtocolIssue("Handler for $method failed: ${e.message}")
                errorReply(id, JsonRpcException.INTERNAL_ERROR, e.message, null)
            }
        send(reply)
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun notification(
        method: String,
        params: JsonElement?,
    ) {
        try {
            handler.onNotification(method, params)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onProtocolIssue("Handler for $method failed: ${e.message}")
        }
    }

    private suspend fun resolve(
        id: JsonElement,
        message: JsonObject,
    ) {
        val reply = lock.withLock { pending.remove(id.toString()) }
        if (reply == null) {
            onProtocolIssue("Response to unknown request $id")
            return
        }
        val error = message["error"] as? JsonObject
        if (error != null) {
            val code = error["code"]?.jsonPrimitive?.intOrNull ?: JsonRpcException.INTERNAL_ERROR
            val text = (error["message"] as? JsonPrimitive)?.contentOrNull ?: "Unknown error"
            reply.completeExceptionally(JsonRpcException(code, text, error["data"]))
        } else {
            reply.complete(message["result"] ?: JsonNull)
        }
    }

    private fun errorReply(
        id: JsonElement,
        code: Int,
        text: String?,
        data: JsonElement?,
    ) = message {
        put("id", id)
        put(
            "error",
            buildJsonObject {
                put("code", code)
                put("message", text ?: "Error")
                data?.let { put("data", it) }
            },
        )
    }

    private fun message(body: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): String =
        buildJsonObject {
            if (withVersionField) put("jsonrpc", "2.0")
            body()
        }.toString()
}
