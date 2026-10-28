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
import octo.core.agent.AdapterRegistry
import octo.core.agent.AgentEvent
import octo.core.agent.AgentId
import octo.core.agent.Capability
import octo.core.agent.MODEL_OPTION
import octo.core.agent.McpServer
import octo.core.agent.PermissionMode
import octo.core.agent.Role
import octo.core.agent.StopReason
import octo.core.config.OctoConfig
import octo.core.flags.FeatureFlags
import octo.core.git.GitRemote
import octo.core.orchestrator.FakeAdapter
import octo.core.orchestrator.FakeWorkspace
import octo.core.orchestrator.Machine
import octo.core.orchestrator.NoProcesses
import octo.core.orchestrator.RemoteMachine
import octo.core.orchestrator.Setup
import octo.core.orchestrator.UnknownAgentException
import octo.core.orchestrator.UnknownMachineException
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

    private suspend fun TestScope.two(
        capabilities: Set<Capability> = setOf(Capability.Mcp),
        maxChildTasks: Int = OctoConfig.DEFAULT_MAX_CHILD_TASKS,
        defaultPermissionMode: PermissionMode = PermissionMode.Supervised,
        machines: Map<String, Machine> = emptyMap(),
    ): Two {
        var next = 0
        var token = 0
        val messaging = AgentMessaging(backgroundScope, maxChildTasks) { "tok-${'a' + token++}" }
        messaging.bind("http://127.0.0.1:1/mcp")
        val s =
            setup(
                FakeAdapter(capabilities),
                machines = machines,
                newId = { "id${++next}" },
                mcp = messaging,
                defaultPermissionMode = defaultPermissionMode,
            )
        messaging.attach(s.core)
        val task = NewTask(repo = "/src/app", baseRef = "main", agent = AgentId("fake"), title = "A", prompt = "Go")
        val a = s.core.createTask(task)
        val b = s.core.createTask(task.copy(title = "B"))
        // Both agents finish their first prompt, so they start out idle.
        s.adapter.sessions.forEach { it.emit(AgentEvent.TurnEnded(StopReason.EndTurn)) }
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
                listOf("list_tasks", "send_message", "wait_for_idle", "create_task"),
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

            val created =
                call(
                    """{"jsonrpc":"2.0","id":6,"method":"tools/call","params":{"name":"create_task",""" +
                        """"arguments":{"title":"Tests","prompt":"Write tests","model":null}}}""",
                )
            assertEquals("Created task id3. Use send_message and wait_for_idle with this id.", text(created))
            val noPrompt =
                call(
                    """{"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"create_task",""" +
                        """"arguments":{"title":"Tests"}}}""",
                )!!
            assertEquals("true", noPrompt["result"]!!.jsonObject["isError"].toString())
            assertEquals("'prompt' must be a string", text(noPrompt))
        }

    @Test
    fun anAgentStartsAChildTaskInItsOwnRepository() =
        runTest {
            val t = two(defaultPermissionMode = PermissionMode.FullAccess)
            t.s.core.setPermissionMode(t.a, PermissionMode.AutoAcceptEdits)
            val id = t.messaging.createTask(t.a, ChildTask("Tests", "Write tests", model = "big-1"))
            testScheduler.runCurrent()
            val parent =
                t.s.core.state.value[t.a]!!
                    .task
            val child =
                t.s.core.state.value[id]!!
                    .task
            assertEquals(t.a, child.parentId)
            assertEquals("Tests", child.title)
            assertEquals(parent.repo, child.repo)
            assertEquals(parent.agent, child.agent)
            assertEquals("main", child.baseRef)
            assertEquals("big-1", child.options[MODEL_OPTION])
            // The config's default is wider than the parent's mode, so the parent's wins.
            assertEquals(PermissionMode.AutoAcceptEdits, child.permissionMode)
            assertEquals(
                listOf("Write tests"),
                t.s.adapter.sessions
                    .last()
                    .prompts,
            )
        }

    @Test
    fun aChildTaskOnAnotherMachineOpensTheParentsCloneThere() =
        runTest {
            val workspace = FakeWorkspace().apply { root = "/srv/app" }
            val box =
                RemoteMachine(
                    AdapterRegistry(listOf(FakeAdapter()), FeatureFlags()),
                    workspace,
                    NoProcesses,
                    "/srv/octo",
                    mapOf("/src/app" to "/srv/app"),
                    remotes = { GitRemote("ssh://box$it", "ssh") },
                )
            val t = two(machines = mapOf("box" to box))
            val id = t.messaging.createTask(t.a, ChildTask("Remote", "Go", machine = "box", baseRef = "octo/a-1"))
            val child =
                t.s.core.state.value[id]!!
                    .task
            assertEquals("box", child.machine)
            assertEquals("/srv/app", child.repo)
            assertEquals("octo/a-1", child.baseRef)
        }

    @Test
    fun refusesAnUnknownAgentOrMachineAndBadArguments() =
        runTest {
            val t = two()
            assertFailsWith<UnknownAgentException> { t.messaging.createTask(t.a, ChildTask("T", "Go", agent = "nope")) }
            assertFailsWith<UnknownMachineException> {
                t.messaging.createTask(t.a, ChildTask("T", "Go", machine = "nope"))
            }
            assertFailsWith<IllegalArgumentException> { t.messaging.createTask(t.a, ChildTask(" ", "Go")) }
            assertFailsWith<IllegalArgumentException> { t.messaging.createTask(t.a, ChildTask("T", "")) }
            assertFailsWith<IllegalArgumentException> {
                t.messaging.createTask(t.a, ChildTask("T", "Go", model = "--yolo"))
            }
            assertFailsWith<IllegalArgumentException> {
                t.messaging.createTask(t.a, ChildTask("T", "Go", baseRef = "--orphan"))
            }
            assertEquals(2, t.s.core.state.value.tasks.size)
        }

    @Test
    fun theCapStopsARunawayAgent() =
        runTest {
            val t = two(maxChildTasks = 2)
            // A refused attempt does not use up a slot.
            assertFailsWith<UnknownAgentException> { t.messaging.createTask(t.a, ChildTask("T", "Go", agent = "nope")) }
            t.messaging.createTask(t.a, ChildTask("One", "Go"))
            t.s.core.forkTask(t.a)
            val e = assertFailsWith<IllegalArgumentException> { t.messaging.createTask(t.a, ChildTask("Three", "Go")) }
            assertTrue("maxChildTasks" in e.message.orEmpty(), e.message)
            // The cap is per task.
            t.messaging.createTask(t.b, ChildTask("Other", "Go"))
        }

    @Test
    fun aZeroCapTurnsTheToolOff() =
        runTest {
            val t = two(maxChildTasks = 0)
            assertFailsWith<IllegalArgumentException> { t.messaging.createTask(t.a, ChildTask("T", "Go")) }
        }
}
