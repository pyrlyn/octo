package octo.adapters.base

import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JsonRpcPeerTest {
    private class Recorder : JsonRpcHandler {
        val notifications = mutableListOf<String>()

        override suspend fun onRequest(
            id: String,
            method: String,
            params: JsonElement?,
        ): JsonElement =
            when (method) {
                "echo" -> params ?: JsonPrimitive("none")
                else -> throw JsonRpcException(JsonRpcException.METHOD_NOT_FOUND, "No $method")
            }

        override suspend fun onNotification(
            method: String,
            params: JsonElement?,
        ) {
            notifications += method
        }
    }

    private fun parse(line: String) = Json.parseToJsonElement(line).jsonObject

    @Test
    fun requestGetsItsResult() =
        runTest {
            val incoming = Channel<String>(Channel.UNLIMITED)
            val sent = Channel<String>(Channel.UNLIMITED)
            val peer = JsonRpcPeer(backgroundScope, { sent.send(it) }, Recorder())
            backgroundScope.launch { peer.run(incoming.receiveAsFlow()) }

            val reply = async { peer.request("initialize", buildJsonObject { put("v", 1) }) }
            val request = parse(sent.receive())
            assertEquals("2.0", (request["jsonrpc"] as JsonPrimitive).content)
            assertEquals("initialize", (request["method"] as JsonPrimitive).content)
            incoming.send("""{"jsonrpc":"2.0","id":${request["id"]},"result":{"ok":true}}""")
            assertEquals("""{"ok":true}""", reply.await().toString())
        }

    @Test
    fun errorResponseThrows() =
        runTest {
            val incoming = Channel<String>(Channel.UNLIMITED)
            val sent = Channel<String>(Channel.UNLIMITED)
            val peer = JsonRpcPeer(backgroundScope, { sent.send(it) }, Recorder())
            backgroundScope.launch { peer.run(incoming.receiveAsFlow()) }

            val reply = async { runCatching { peer.request("x") } }
            val id = parse(sent.receive())["id"]
            incoming.send("""{"id":$id,"error":{"code":-32000,"message":"nope"}}""")
            val error = reply.await().exceptionOrNull() as JsonRpcException
            assertEquals(-32000, error.code)
            assertEquals("nope", error.message)
        }

    @Test
    fun answersServerRequestsAndHandlesNotifications() =
        runTest {
            val incoming = Channel<String>(Channel.UNLIMITED)
            val sent = Channel<String>(Channel.UNLIMITED)
            val handler = Recorder()
            val peer = JsonRpcPeer(backgroundScope, { sent.send(it) }, handler, withVersionField = false)
            backgroundScope.launch { peer.run(incoming.receiveAsFlow()) }

            incoming.send("""{"id":"a","method":"echo","params":[1]}""")
            val echo = parse(sent.receive())
            assertFalse("jsonrpc" in echo)
            assertEquals("\"a\"", echo["id"].toString())
            assertEquals("[1]", echo["result"].toString())

            incoming.send("""{"id":7,"method":"missing"}""")
            val missing = parse(sent.receive())
            assertEquals("7", missing["id"].toString())
            assertTrue(missing["error"].toString().contains("-32601"))

            incoming.send("""{"method":"session/update"}""")
            incoming.send("""{"id":8,"method":"echo"}""")
            sent.receive()
            assertEquals(listOf("session/update"), handler.notifications)
        }

    @Test
    fun malformedLinesAreReported() =
        runTest {
            val incoming = Channel<String>(Channel.UNLIMITED)
            val issues = mutableListOf<String>()
            val peer = JsonRpcPeer(backgroundScope, {}, Recorder(), onProtocolIssue = { issues += it })
            incoming.send("not json")
            incoming.send("""{"id":99,"result":1}""")
            incoming.close()
            peer.run(incoming.receiveAsFlow())
            assertEquals(2, issues.size)
        }

    @Test
    fun pendingRequestsFailWhenTheStreamEnds() =
        runTest {
            val incoming = Channel<String>(Channel.UNLIMITED)
            val sent = Channel<String>(Channel.UNLIMITED)
            val peer = JsonRpcPeer(backgroundScope, { sent.send(it) }, Recorder())
            val runner = backgroundScope.launch { peer.run(incoming.receiveAsFlow()) }
            val reply = async { runCatching { peer.request("slow") } }
            sent.receive()
            incoming.close()
            runner.join()
            assertTrue(reply.await().exceptionOrNull() is JsonRpcException)
            assertFailsWith<JsonRpcException> { peer.request("late") }
        }
}
