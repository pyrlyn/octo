package octo.core.access

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import octo.core.agent.AgentEvent
import octo.core.agent.PermissionKind
import octo.core.agent.PermissionMode
import octo.core.agent.PermissionOption
import octo.core.agent.RequestResponse
import octo.core.agent.VendorTerms
import octo.core.io.AtomicFile
import octo.core.orchestrator.FakeAdapter
import octo.core.orchestrator.Setup
import octo.core.orchestrator.setup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteApiTest {
    private class Api(
        val s: Setup,
        val api: RemoteApi,
        val token: String,
    ) {
        suspend fun call(
            method: String,
            path: String,
            body: String? = null,
            token: String? = this.token,
        ) = api.handle(ApiRequest(method, "/api/v1$path", token?.let { "Bearer $it" }, body))
    }

    private suspend fun TestScope.api(adapter: FakeAdapter = FakeAdapter()): Api {
        val s = setup(adapter)
        val devices = Devices.open(AtomicFile(Path(s.home, "devices.json")), FakeSecrets())
        val api = RemoteApi(s.core, devices)
        val code = devices.startPairing().code
        val paired = api.handle(ApiRequest("POST", "/api/v1/pair", null, """{"code":"$code","name":"Phone"}"""))
        assertEquals(200, paired.status)
        val token =
            paired.body!!
                .jsonObject["token"]!!
                .jsonPrimitive.content
        return Api(s, api, token)
    }

    private val newTask = """{"repo":"/src/app","baseRef":"main","agent":"fake","title":"Fix it","prompt":"Go"}"""

    private val JsonObject.error get() = this["error"]!!.jsonPrimitive.content

    @Test
    fun onlyAPairedDeviceGetsIn() =
        runTest {
            val t = api()
            assertEquals(200, t.call("GET", "/tasks").status)
            assertEquals(401, t.call("GET", "/tasks", token = null).status)
            assertEquals(401, t.call("GET", "/tasks", token = "guess").status)
            assertEquals(401, t.call("GET", "/nowhere", token = null).status, "no route is probed without a token")
            val wrong = t.api.handle(ApiRequest("POST", "/api/v1/pair", null, """{"code":"NOPE","name":"X"}"""))
            assertEquals(401, wrong.status)
            assertEquals(404, t.api.handle(ApiRequest("GET", "/other", null, null)).status)
        }

    @Test
    fun aDeviceRunsATaskLikeTheWindowDoes() =
        runTest {
            val t = api()
            val created = t.call("POST", "/tasks", newTask)
            assertEquals(201, created.status)
            val id =
                created.body!!
                    .jsonObject["id"]!!
                    .jsonPrimitive.content
            testScheduler.runCurrent()

            val task =
                t
                    .call("GET", "/tasks")
                    .body!!
                    .jsonObject["tasks"]!!
                    .jsonArray
                    .single()
                    .jsonObject
            assertEquals("Fix it", task["title"]!!.jsonPrimitive.content)
            assertEquals("fake", task["agent"]!!.jsonPrimitive.content)
            val session =
                t.s.adapter.sessions
                    .single()
            assertEquals(listOf("Go"), session.prompts)

            assertEquals(204, t.call("POST", "/tasks/$id/prompt", """{"text":"More"}""").status)
            assertEquals(listOf("Go", "More"), session.prompts)
            assertEquals(204, t.call("POST", "/tasks/$id/cancel").status)
            assertEquals(1, session.cancelled)

            val allow = PermissionOption("ok", "Allow", PermissionKind.AllowOnce)
            session.emit(AgentEvent.TurnStarted, AgentEvent.PermissionRequested("p1", "Run ls", listOf(allow)))
            testScheduler.runCurrent()
            val pending =
                t
                    .call("GET", "/tasks")
                    .body!!
                    .jsonObject["tasks"]!!
                    .jsonArray[0]
                    .jsonObject["pending"]!!
            assertEquals(
                "permission",
                pending.jsonArray
                    .single()
                    .jsonObject["kind"]!!
                    .jsonPrimitive.content,
            )
            assertEquals(204, t.call("POST", "/tasks/$id/respond", """{"requestId":"p1","optionId":"ok"}""").status)
            assertEquals(listOf<Pair<String, RequestResponse>>("p1" to RequestResponse.Option("ok")), session.responses)

            val transcript = t.call("GET", "/tasks/$id/transcript")
            assertEquals(200, transcript.status)
            assertTrue(
                transcript.body!!
                    .jsonObject["entries"]!!
                    .jsonArray
                    .isNotEmpty(),
            )
            assertEquals(204, t.call("POST", "/tasks/$id/stop").status)
        }

    @Test
    fun aDeviceChoosesThePermissionMode() =
        runTest {
            val t = api()
            val bad = newTask.replace("}", ""","permissionMode":"Yolo"}""")
            assertEquals(400, t.call("POST", "/tasks", bad).status)
            val created = t.call("POST", "/tasks", newTask.replace("}", ""","permissionMode":"Auto"}"""))
            assertEquals(201, created.status)
            assertEquals(
                PermissionMode.Auto,
                t.s.core.state.value.tasks
                    .single()
                    .task.permissionMode,
            )
        }

    @Test
    fun mistakesGetClearErrors() =
        runTest {
            val t = api()
            assertEquals(404, t.call("POST", "/tasks/nope/prompt", """{"text":"x"}""").status)
            assertEquals(404, t.call("GET", "/tasks/nope/transcript").status)
            assertEquals(400, t.call("POST", "/tasks", "{not json").status)
            assertEquals(400, t.call("POST", "/tasks", """{"repo":"/src/app"}""").status)
            assertEquals(400, t.call("POST", "/tasks", newTask.replace("\"fake\"", "\"ghost\"")).status)
            val id =
                t
                    .call("POST", "/tasks", newTask)
                    .body!!
                    .jsonObject["id"]!!
                    .jsonPrimitive.content
            assertEquals(400, t.call("POST", "/tasks/$id/prompt", """{"text":" "}""").status)
            val both = """{"requestId":"p1","optionId":"ok","cancelled":true}"""
            assertEquals(400, t.call("POST", "/tasks/$id/respond", both).status)
            val get = t.call("GET", "/tasks/$id/prompt")
            assertEquals(405, get.status)
            assertEquals("POST", get.allow)
            assertEquals("GET, POST", t.call("DELETE", "/tasks").allow)
            assertEquals(404, t.call("POST", "/tasks/$id/explode").status)
        }

    @Test
    fun vendorTermsAreAcceptedOnlyInTheWindow() =
        runTest {
            val t = api(FakeAdapter(terms = VendorTerms("1", "Read this.", "https://example.com")))
            val refused = t.call("POST", "/tasks", newTask)
            assertEquals(409, refused.status)
            assertTrue("Octo window" in refused.body!!.jsonObject.error)
            assertEquals(
                emptyList(),
                t.s.workspace.worktrees.keys
                    .toList(),
            )
        }
}
