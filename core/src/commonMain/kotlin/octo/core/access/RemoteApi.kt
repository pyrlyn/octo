package octo.core.access

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import octo.core.agent.AgentId
import octo.core.agent.PermissionMode
import octo.core.agent.PermissionOption
import octo.core.agent.Question
import octo.core.agent.RequestResponse
import octo.core.http.bearerToken
import octo.core.orchestrator.OctoCore
import octo.core.orchestrator.UnknownTaskException
import octo.core.status.AgentStatus
import octo.core.task.NewTask
import octo.core.task.PendingRequest
import octo.core.task.SidebarStatus
import octo.core.task.TaskId
import octo.core.task.TaskView
import octo.core.task.TranscriptEntry
import octo.core.terms.TermsNotAcknowledgedException

/** One call as the transport hands it over; [body] is null when there was none. */
data class ApiRequest(
    val method: String,
    val path: String,
    val authorization: String?,
    val body: String?,
)

/** The answer; [allow] lists the methods a 405 accepts. */
data class ApiResponse(
    val status: Int,
    val body: JsonElement? = null,
    val allow: String? = null,
)

/**
 * The JSON API a paired device uses, kept free of any transport. Every call but `POST /pair`
 * needs a paired device's token; the device is the user's own, so it may do what the window
 * does, except accept vendor terms, which the user reads in the window.
 */
class RemoteApi(
    private val core: OctoCore,
    private val devices: Devices,
) {
    @Suppress("TooGenericExceptionCaught")
    suspend fun handle(request: ApiRequest): ApiResponse =
        try {
            route(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: PairingException) {
            failure(UNAUTHORIZED, e)
        } catch (e: TermsNotAcknowledgedException) {
            failure(CONFLICT, e, "Accept the agent's terms in the Octo window first")
        } catch (e: UnknownTaskException) {
            failure(NOT_FOUND, e)
        } catch (e: IllegalArgumentException) {
            // Also SerializationException: the body did not match the call.
            failure(BAD_REQUEST, e)
        } catch (e: IllegalStateException) {
            failure(CONFLICT, e)
        } catch (e: Exception) {
            failure(SERVER_ERROR, e)
        }

    private suspend fun route(request: ApiRequest): ApiResponse {
        val path = request.path.removeSuffix("/")
        val segments = path.removePrefix(PREFIX).split('/').drop(1)
        return when {
            !path.startsWith("$PREFIX/") -> {
                error(NOT_FOUND, "Not found")
            }

            segments == listOf("pair") -> {
                post(request, PairBody.serializer()) { pair(it) }
            }

            devices.device(bearerToken(request.authorization)) == null -> {
                error(UNAUTHORIZED, "This call needs a paired device's token")
            }

            segments == listOf("tasks") -> {
                tasks(request)
            }

            segments.size == TASK_CALL && segments[0] == "tasks" -> {
                task(request, TaskId(segments[1]), segments[2])
            }

            else -> {
                error(NOT_FOUND, "Not found")
            }
        }
    }

    private suspend fun pair(body: PairBody): ApiResponse {
        val token = devices.pair(body.code, body.name)
        val device = devices.device(token)
        return ok(
            buildJsonObject {
                put("token", token)
                put("deviceId", device?.id)
            },
        )
    }

    private suspend fun tasks(request: ApiRequest): ApiResponse =
        when (request.method) {
            GET -> {
                ok(
                    TaskList.serializer(),
                    TaskList(
                        core.state.value.tasks
                            .map { it.dto() },
                    ),
                )
            }

            POST -> {
                post(request, NewTaskBody.serializer()) { create(it) }
            }

            else -> {
                ApiResponse(METHOD_NOT_ALLOWED, allow = "$GET, $POST")
            }
        }

    private suspend fun create(body: NewTaskBody): ApiResponse {
        require(body.repo.isNotBlank() && body.baseRef.isNotBlank()) { "repo and baseRef are required" }
        require(body.agent.isNotBlank() && body.title.isNotBlank()) { "agent and title are required" }
        val id =
            core.createTask(
                NewTask(
                    repo = body.repo,
                    baseRef = body.baseRef,
                    agent = AgentId(body.agent),
                    title = body.title.trim(),
                    prompt = body.prompt?.takeIf { it.isNotBlank() },
                    options = body.options,
                    machine = body.machine,
                    permissionMode = body.permissionMode,
                ),
            )
        return ApiResponse(CREATED, buildJsonObject { put("id", id.value) })
    }

    private suspend fun task(
        request: ApiRequest,
        id: TaskId,
        action: String,
    ): ApiResponse {
        if (core.state.value[id] == null) throw UnknownTaskException(id)
        return when (action) {
            "transcript" -> {
                if (request.method != GET) {
                    ApiResponse(METHOD_NOT_ALLOWED, allow = GET)
                } else {
                    ok(Transcript.serializer(), Transcript(core.transcript(id).first()))
                }
            }

            "prompt" -> {
                post(request, PromptBody.serializer()) {
                    require(it.text.isNotBlank()) { "text is required" }
                    core.sendPrompt(id, it.text)
                    ApiResponse(NO_CONTENT)
                }
            }

            "respond" -> {
                post(request, RespondBody.serializer()) {
                    core.respond(id, it.requestId, it.response())
                    ApiResponse(NO_CONTENT)
                }
            }

            "cancel", "stop", "restart" -> {
                if (request.method != POST) {
                    ApiResponse(METHOD_NOT_ALLOWED, allow = POST)
                } else {
                    when (action) {
                        "cancel" -> core.cancel(id)
                        "stop" -> core.stop(id)
                        else -> core.restart(id)
                    }
                    ApiResponse(NO_CONTENT)
                }
            }

            else -> {
                error(NOT_FOUND, "Not found")
            }
        }
    }

    private suspend fun <T> post(
        request: ApiRequest,
        serializer: KSerializer<T>,
        call: suspend (T) -> ApiResponse,
    ): ApiResponse =
        if (request.method != POST) {
            ApiResponse(METHOD_NOT_ALLOWED, allow = POST)
        } else {
            call(json.decodeFromString(serializer, request.body.orEmpty()))
        }

    @Serializable
    private data class PairBody(
        val code: String,
        val name: String,
    )

    @Serializable
    private data class NewTaskBody(
        val repo: String,
        val baseRef: String,
        val agent: String,
        val title: String,
        val prompt: String? = null,
        val options: Map<String, String> = emptyMap(),
        val machine: String? = null,
        /** One of [PermissionMode]'s names; left out, the config default. */
        val permissionMode: PermissionMode? = null,
    )

    @Serializable
    private data class PromptBody(
        val text: String,
    )

    /** Exactly one of [optionId], [answers] and [cancelled]. */
    @Serializable
    private data class RespondBody(
        val requestId: String,
        val optionId: String? = null,
        val answers: Map<String, String>? = null,
        val cancelled: Boolean = false,
    ) {
        fun response(): RequestResponse {
            val given = listOfNotNull(optionId, answers, cancelled.takeIf { it })
            require(given.size == 1) { "Give exactly one of optionId, answers and cancelled" }
            return when {
                optionId != null -> RequestResponse.Option(optionId)
                answers != null -> RequestResponse.Answers(answers)
                else -> RequestResponse.Cancelled
            }
        }
    }

    @Serializable
    private data class TaskList(
        val tasks: List<TaskDto>,
    )

    @Serializable
    private data class Transcript(
        val entries: List<TranscriptEntry>,
    )

    @Serializable
    private data class TaskDto(
        val id: TaskId,
        val title: String,
        val repo: String,
        val branch: String,
        val baseRef: String,
        val agent: String,
        val machine: String?,
        val status: AgentStatus,
        val sidebarStatus: SidebarStatus,
        val failure: String?,
        val pending: List<PendingDto>,
    )

    @Serializable
    private data class PendingDto(
        val id: String,
        val kind: String,
        val title: String? = null,
        val options: List<PermissionOption> = emptyList(),
        val questions: List<Question> = emptyList(),
    )

    private fun TaskView.dto() =
        TaskDto(
            id = task.id,
            title = task.title,
            repo = task.repo,
            branch = task.branch,
            baseRef = task.baseRef,
            agent = task.agent.value,
            machine = task.machine,
            status = status,
            sidebarStatus = sidebarStatus,
            failure = failure,
            pending =
                pending.map {
                    when (it) {
                        is PendingRequest.Permission -> PendingDto(it.id, "permission", it.title, options = it.options)
                        is PendingRequest.Questions -> PendingDto(it.id, "questions", questions = it.questions)
                    }
                },
        )

    private fun <T> ok(
        serializer: KSerializer<T>,
        value: T,
    ) = ok(json.encodeToJsonElement(serializer, value))

    private fun ok(body: JsonElement) = ApiResponse(OK, body)

    private fun failure(
        status: Int,
        e: Exception,
        message: String? = null,
    ) = error(status, message ?: e.message ?: e::class.simpleName ?: "Failed")

    private fun error(
        status: Int,
        message: String,
    ) = ApiResponse(status, buildJsonObject { put("error", message) })

    companion object {
        const val PREFIX = "/api/v1"

        /** `tasks/{id}/{action}`. */
        private const val TASK_CALL = 3
        private const val GET = "GET"
        private const val POST = "POST"
        private const val OK = 200
        private const val CREATED = 201
        private const val NO_CONTENT = 204
        private const val BAD_REQUEST = 400
        private const val UNAUTHORIZED = 401
        private const val NOT_FOUND = 404
        private const val METHOD_NOT_ALLOWED = 405
        private const val CONFLICT = 409
        private const val SERVER_ERROR = 500

        private val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                explicitNulls = false
            }
    }
}
