package octo.adapters.cursor

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import octo.adapters.base.AcpConfig
import octo.adapters.base.AcpSession
import octo.adapters.base.array
import octo.adapters.base.bool
import octo.adapters.base.obj
import octo.adapters.base.string
import octo.core.agent.AgentEvent
import octo.core.agent.PermissionKind
import octo.core.agent.PermissionOption
import octo.core.agent.PlanEntry
import octo.core.agent.Question
import octo.core.agent.RequestResponse
import octo.core.agent.Role
import octo.core.agent.ToolStatus
import octo.core.process.RunningProcess
import kotlin.coroutines.CoroutineContext

/** Cursor's ACP extensions, as documented at https://cursor.com/docs/cli/acp. */
internal class CursorSession(
    process: RunningProcess,
    context: CoroutineContext,
    config: AcpConfig,
) : AcpSession(process, context, config) {
    private val lock = Mutex()
    private val todos = linkedMapOf<String, JsonObject>()

    override suspend fun onExtensionRequest(
        id: String,
        method: String,
        params: JsonObject,
    ): JsonElement =
        when (method) {
            "cursor/ask_question" -> askQuestion(id, params)
            "cursor/create_plan" -> createPlan(id, params)
            else -> super.onExtensionRequest(id, method, params)
        }

    override suspend fun onExtensionNotification(
        method: String,
        params: JsonObject,
    ) {
        when (method) {
            "cursor/update_todos" -> {
                updateTodos(params.array("todos"), merge = params.bool("merge") == true)
            }

            "cursor/task" -> {
                val id = params.string("toolCallId") ?: return
                val title = "Task: ${params.string("description") ?: "subagent"}"
                emit(AgentEvent.ToolCall(id, title, "other", ToolStatus.InProgress, params.string("prompt")))
            }
        }
    }

    private suspend fun askQuestion(
        id: String,
        params: JsonObject,
    ): JsonElement {
        flush()
        val asked = params.array("questions").mapNotNull { it.obj() }.filter { it.string("id") != null }
        val questions =
            asked.map { q ->
                Question(
                    id = q.string("id").orEmpty(),
                    text = q.string("prompt").orEmpty(),
                    options = options(q).map { it.second },
                    multiSelect = q.bool("allowMultiple") == true,
                )
            }
        val response = requests.ask(id, AgentEvent.QuestionAsked(id, questions))
        if (response !is RequestResponse.Answers) return outcome("cancelled")
        return outcome("answered") {
            putJsonArray("answers") {
                asked.forEach { q ->
                    val qid = q.string("id").orEmpty()
                    val picked = response.answers[qid]?.split(RequestResponse.Answers.SEPARATOR).orEmpty()
                    val ids = options(q).filter { (_, label) -> label in picked }.map { it.first }
                    add(
                        buildJsonObject {
                            put("questionId", qid)
                            put("selectedOptionIds", buildJsonArray { ids.forEach { add(JsonPrimitive(it)) } })
                        },
                    )
                }
            }
        }
    }

    private suspend fun createPlan(
        id: String,
        params: JsonObject,
    ): JsonElement {
        flush()
        updateTodos(params.array("todos"), merge = false)
        params.string("plan")?.let { emit(AgentEvent.Message(Role.Assistant, it)) }
        val title = "Plan: ${params.string("name") ?: "approve the plan"}"
        val response = requests.ask(id, AgentEvent.PermissionRequested(id, title, PLAN_OPTIONS))
        return when ((response as? RequestResponse.Option)?.optionId) {
            ACCEPT -> outcome("accepted")
            REJECT -> outcome("rejected")
            else -> outcome("cancelled")
        }
    }

    private suspend fun updateTodos(
        list: List<JsonElement>,
        merge: Boolean,
    ) {
        val entries =
            lock.withLock {
                if (!merge) todos.clear()
                list.mapNotNull { it.obj() }.forEach { todo -> todo.string("id")?.let { todos[it] = todo } }
                // A cancelled todo is no longer part of the plan.
                todos.values.filter { it.string("status") != "cancelled" }.map {
                    PlanEntry(it.string("content").orEmpty(), done = it.string("status") == "completed")
                }
            }
        emit(AgentEvent.PlanUpdated(entries))
    }

    companion object {
        const val ACCEPT = "accept"
        const val REJECT = "reject"

        private val PLAN_OPTIONS =
            listOf(
                PermissionOption(ACCEPT, "Accept plan", PermissionKind.AllowOnce),
                PermissionOption(REJECT, "Reject plan", PermissionKind.RejectOnce),
            )

        private fun options(q: JsonObject): List<Pair<String, String>> =
            q.array("options").mapNotNull { it.obj() }.mapNotNull { o ->
                val id = o.string("id") ?: return@mapNotNull null
                id to (o.string("label") ?: id)
            }

        private fun outcome(
            value: String,
            body: JsonObjectBuilder.() -> Unit = {},
        ): JsonElement =
            buildJsonObject {
                putJsonObject("outcome") {
                    put("outcome", value)
                    body()
                }
            }
    }
}
