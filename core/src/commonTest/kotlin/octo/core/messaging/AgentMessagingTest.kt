package octo.core.messaging

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import octo.core.agent.AgentEvent
import octo.core.agent.AgentId
import octo.core.agent.Capability
import octo.core.agent.McpServer
import octo.core.agent.Role
import octo.core.agent.StopReason
import octo.core.orchestrator.FakeAdapter
import octo.core.orchestrator.Setup
import octo.core.orchestrator.setup
import octo.core.status.AgentStatus
import octo.core.task.NewTask
import octo.core.task.TaskId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AgentMessagingTest {
    private class Two(
        val s: Setup,
        val messaging: AgentMessaging,
        val a: TaskId,
        val b: TaskId,
    )

    private suspend fun TestScope.two(capabilities: Set<Capability> = setOf(Capability.Mcp)): Two {
        var next = 0
        val tokens = listOf("tok-a", "tok-b", "tok-c").iterator()
        val messaging = AgentMessaging(backgroundScope) { tokens.next() }
        messaging.bind("http://127.0.0.1:1/mcp")
        val s = setup(FakeAdapter(capabilities), newId = { "id${++next}" }, mcp = messaging)
        messaging.attach(s.core)
        val task = NewTask(repo = "/src/app", baseRef = "main", agent = AgentId("fake"), title = "A", prompt = "Go")
        val a = s.core.createTask(task)
        val b = s.core.createTask(task.copy(title = "B"))
        testScheduler.runCurrent()
        return Two(s, messaging, a, b)
    }

    @Test
    fun agentsWithMcpGetTheServerAndTheirOwnToken() =
        runTest {
            val t = two()
            val specs = t.s.adapter.specs
            assertEquals(
                listOf(McpServer("octo", "http://127.0.0.1:1/mcp", mapOf("Authorization" to "Bearer tok-a"))),
                specs[0].mcpServers,
            )
            assertEquals("Bearer tok-b", specs[1].mcpServers.single().headers["Authorization"])
            assertEquals(t.a, t.messaging.caller("Bearer tok-a"))
            assertEquals(t.b, t.messaging.caller("Bearer tok-b"))
            assertNull(t.messaging.caller("tok-a"))
            assertNull(t.messaging.caller("Bearer nope"))
            assertNull(t.messaging.caller(null))
        }

    @Test
    fun agentsWithoutMcpGetNoServer() =
        runTest {
            val t = two(capabilities = emptySet())
            assertTrue(
                t.s.adapter.specs
                    .all { it.mcpServers.isEmpty() },
            )
        }

    @Test
    fun aMessageWaitsUntilTheTargetIsIdle() =
        runTest {
            val t = two()
            val target = t.s.adapter.sessions[1]
            target.emit(AgentEvent.TurnStarted)
            testScheduler.runCurrent()

            t.messaging.send(t.a, t.b, "Which port do you use?")
            testScheduler.runCurrent()
            assertEquals(listOf("Go"), target.prompts)

            target.emit(AgentEvent.TurnEnded(StopReason.EndTurn))
            testScheduler.runCurrent()
            val relayed = target.prompts.drop(1).single()
            assertTrue("Which port do you use?" in relayed)
            assertTrue("\"A\" (id ${t.a.value})" in relayed)
        }

    @Test
    fun refusesBadMessages() =
        runTest {
            val t = two()
            assertFailsWith<IllegalArgumentException> { t.messaging.send(t.a, t.a, "me") }
            assertFailsWith<IllegalArgumentException> { t.messaging.send(t.a, TaskId("gone"), "hi") }
            assertFailsWith<IllegalArgumentException> { t.messaging.send(t.a, t.b, "  ") }
            assertFailsWith<IllegalArgumentException> {
                t.messaging.send(t.a, t.b, "x".repeat(AgentMessaging.MAX_MESSAGE + 1))
            }
            t.s.core.stop(t.b)
            assertFailsWith<IllegalArgumentException> { t.messaging.send(t.a, t.b, "hi") }
        }

    @Test
    fun waitForIdleReturnsTheLastReply() =
        runTest {
            val t = two()
            val target = t.s.adapter.sessions[1]
            target.emit(AgentEvent.TurnStarted)
            testScheduler.runCurrent()

            val timedOut = t.messaging.waitForIdle(t.a, t.b, timeoutMs = 1_000)
            assertFalse(timedOut.idle)
            assertEquals(AgentStatus.Working, timedOut.status)

            val waiting = async { t.messaging.waitForIdle(t.a, t.b, timeoutMs = 60_000) }
            target.emit(AgentEvent.Message(Role.Assistant, "Port 8080."), AgentEvent.TurnEnded(StopReason.EndTurn))
            val done = waiting.await()
            assertTrue(done.idle)
            assertEquals("Port 8080.", done.lastReply)
        }

    @Test
    fun theMcpHandlerSpeaksJsonRpc() =
        runTest {
            val t = two()
            val handler = McpHandler(t.messaging)

            suspend fun call(request: String): JsonObject? = handler.handle(t.a, Json.parseToJsonElement(request))

            val init =
                call(
                    """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18"}}""",
                )!!
            assertEquals("2025-06-18", init["result"]!!.jsonObject["protocolVersion"]!!.jsonPrimitive.content)
            assertNull(call("""{"jsonrpc":"2.0","method":"notifications/initialized"}"""))
            val unknown = call("""{"jsonrpc":"2.0","id":"probe","method":"server/discover"}""")!!
            assertEquals(McpHandler.METHOD_NOT_FOUND.toString(), unknown["error"]!!.jsonObject["code"].toString())

            val tools = call("""{"jsonrpc":"2.0","id":2,"method":"tools/list"}""")!!
            assertEquals(
                listOf("list_tasks", "send_message", "wait_for_idle"),
                tools["result"]!!.jsonObject["tools"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content },
            )

            fun text(response: JsonObject?) =
                response!!["result"]!!
                    .jsonObject["content"]!!
                    .jsonArray[0]
                    .jsonObject["text"]!!
                    .jsonPrimitive.content

            val list = call("""{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"list_tasks"}}""")
            assertEquals("${t.b.value}\tIdle\tfake\tB", text(list))

            val bad =
                call(
                    """{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"send_message","arguments":{"task":1}}}""",
                )!!
            assertEquals("true", bad["result"]!!.jsonObject["isError"].toString())

            val sent =
                call(
                    """{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"send_message",""" +
                        """"arguments":{"task":"${t.b.value}","text":"hello"}}}""",
                )
            assertTrue(text(sent).startsWith("Queued"))
        }
}
