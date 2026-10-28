package octo.core.messaging

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import octo.core.agent.AgentId
import octo.core.agent.MODEL_OPTION
import octo.core.agent.McpServer
import octo.core.agent.Role
import octo.core.config.OctoConfig
import octo.core.http.bearerToken
import octo.core.orchestrator.OctoCore
import octo.core.status.AgentStatus
import octo.core.task.NewTask
import octo.core.task.OctoState
import octo.core.task.TaskId
import octo.core.task.TranscriptEntry
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Gives each starting agent the MCP servers Octo runs for it. */
fun interface McpProvider {
    fun servers(task: TaskId): List<McpServer>
}

data class TaskSummary(
    val id: TaskId,
    val title: String,
    val agent: String,
    val status: AgentStatus,
)

/** A task an agent asks for; every null takes its parent's value. */
data class ChildTask(
    val title: String,
    val prompt: String,
    val agent: String? = null,
    val model: String? = null,
    val baseRef: String? = null,
    val machine: String? = null,
)

data class WaitResult(
    val status: AgentStatus,
    /** False when the wait ran out first. */
    val idle: Boolean,
    val lastReply: String?,
)

/**
 * Agent-to-agent messages. A message is queued for its target and handed over as a prompt once the
 * target is idle, so a busy agent is never interrupted. Every call names its sender by the token
 * Octo gave that task's agent, never by anything the agent says about itself.
 */
class AgentMessaging(
    private val scope: CoroutineScope,
    /** Child tasks one task may have, forks included; 0 refuses every [createTask]. */
    private val maxChildTasks: Int = OctoConfig.DEFAULT_MAX_CHILD_TASKS,
    private val newToken: () -> String = ::randomToken,
) : McpProvider {
    private val endpoint = MutableStateFlow<String?>(null)
    private val tokens = MutableStateFlow<Map<TaskId, String>>(emptyMap())
    private val queues = MutableStateFlow<Map<TaskId, List<String>>>(emptyMap())

    // Children still being set up are not in the state yet but must count against the cap.
    private val creating = MutableStateFlow<Map<TaskId, Int>>(emptyMap())

    // Delivered but not yet picked up: the target still reads as idle until its turn starts.
    private val inFlight = MutableStateFlow<Set<TaskId>>(emptySet())
    private val attached = CompletableDeferred<OctoCore>()

    /** The URL the HTTP transport listens on; until then agents get no server. */
    fun bind(url: String) {
        endpoint.value = url
    }

    override fun servers(task: TaskId): List<McpServer> {
        val url = endpoint.value ?: return emptyList()
        val token =
            tokens
                .updateAndGet { if (task in it) it else it + (task to newToken()) }
                .getValue(task)
        return listOf(McpServer(SERVER_NAME, url, mapOf("Authorization" to "Bearer $token")))
    }

    /** The task whose agent sent [authorization], or null for a missing or unknown token. */
    fun caller(authorization: String?): TaskId? {
        val token = bearerToken(authorization) ?: return null
        return tokens.value.entries
            .firstOrNull { it.value == token }
            ?.key
    }

    /** Starts delivering; the orchestrator is built after the provider it needs, so it comes in late. */
    fun attach(core: OctoCore) {
        if (!attached.complete(core)) return
        scope.launch {
            combine(core.state, queues, inFlight, ::Triple).collect { (state, queued, flying) ->
                deliver(core, state, queued, flying)
            }
        }
    }

    suspend fun listTasks(caller: TaskId): List<TaskSummary> =
        attached
            .await()
            .state.value.tasks
            .filter { it.task.id != caller }
            .map { TaskSummary(it.task.id, it.task.title, it.task.agent.value, it.status) }

    suspend fun send(
        caller: TaskId,
        to: TaskId,
        text: String,
    ) {
        val state = attached.await().state.value
        require(to != caller) { "A task cannot message itself" }
        val target = state[to] ?: throw IllegalArgumentException("No task '${to.value}'")
        require(text.isNotBlank()) { "The message is empty" }
        require(text.length <= MAX_MESSAGE) { "The message is longer than $MAX_MESSAGE characters" }
        require(target.status !in ENDED) { "Task '${to.value}' is not running" }
        val title = state[caller]?.task?.title ?: caller.value
        val prompt =
            "Message from the Octo task \"$title\" (id ${caller.value}), relayed by Octo:\n\n$text\n\n" +
                "If it needs an answer, reply with Octo's send_message tool to task ${caller.value}."
        queues.update { all ->
            val queue = all[to].orEmpty()
            require(queue.size < MAX_QUEUED) { "Task '${to.value}' already has $MAX_QUEUED messages waiting" }
            all + (to to queue + prompt)
        }
    }

    /**
     * Starts [child] in the caller's repository as a child of the caller, through the same path as a
     * task the user starts: the terms gate, the start queue and a permission mode no wider than the
     * caller's.
     */
    suspend fun createTask(
        caller: TaskId,
        child: ChildTask,
    ): TaskId {
        val core = attached.await()
        require(child.title.isNotBlank()) { "The title is empty" }
        require(child.title.length <= MAX_TITLE) { "The title is longer than $MAX_TITLE characters" }
        require(child.prompt.isNotBlank()) { "The prompt is empty" }
        require(child.prompt.length <= MAX_MESSAGE) { "The prompt is longer than $MAX_MESSAGE characters" }
        // Both reach a command line (the agent CLI, git), where a leading dash would read as an option.
        child.model?.let { require(it.matches(MODEL)) { "'$it' is not a model name" } }
        child.baseRef?.let { require(it.matches(REF)) { "'$it' is not a git ref" } }
        val parent = core.state.value[caller]?.task ?: throw IllegalArgumentException("No task '${caller.value}'")
        creating.update { all ->
            // Counted inside the update: a child that finished being created between a read outside
            // it and this check would slip past the cap.
            val children =
                core.state.value.tasks
                    .count { it.task.parentId == caller }
            val pending = all[caller] ?: 0
            require(children + pending < maxChildTasks) {
                "This task already has $maxChildTasks child tasks; maxChildTasks in config.json sets the cap"
            }
            all + (caller to pending + 1)
        }
        try {
            val agent = child.agent?.let(::AgentId) ?: parent.agent
            val options = if (agent == parent.agent) parent.options else emptyMap()
            return core.createTask(
                NewTask(
                    repo = parent.repo,
                    baseRef = child.baseRef ?: parent.baseRef,
                    agent = agent,
                    title = child.title,
                    prompt = child.prompt,
                    options = options + listOfNotNull(child.model?.let { MODEL_OPTION to it }),
                    machine = child.machine ?: parent.machine,
                    parentId = caller,
                ),
            )
        } finally {
            creating.update { all ->
                val left = (all[caller] ?: 1) - 1
                if (left > 0) all + (caller to left) else all - caller
            }
        }
    }

    /** Waits until [task] is idle with nothing left to deliver; returns its last reply either way. */
    suspend fun waitForIdle(
        caller: TaskId,
        task: TaskId,
        timeoutMs: Long,
    ): WaitResult {
        val core = attached.await()
        require(task != caller) { "A task cannot wait on itself" }
        if (core.state.value[task] == null) throw IllegalArgumentException("No task '${task.value}'")
        val settled =
            withTimeoutOrNull(timeoutMs.coerceIn(0, MAX_WAIT_MS)) {
                combine(core.state, queues, inFlight, ::Triple).first { (state, queued, flying) ->
                    val status = state[task]?.status
                    status == null ||
                        status in ENDED ||
                        (status == AgentStatus.Idle && queued[task].isNullOrEmpty() && task !in flying)
                }
            }
        val status = core.state.value[task]?.status ?: AgentStatus.Stopped
        val reply =
            core
                .transcript(task)
                .first()
                .lastOrNull { it is TranscriptEntry.Text && it.role == Role.Assistant && !it.partial }
                ?.let { (it as TranscriptEntry.Text).text }
        return WaitResult(status, idle = settled != null && status == AgentStatus.Idle, lastReply = reply)
    }

    private fun deliver(
        core: OctoCore,
        state: OctoState,
        queued: Map<TaskId, List<String>>,
        flying: Set<TaskId>,
    ) {
        flying.filter { state[it]?.status != AgentStatus.Idle }.takeIf { it.isNotEmpty() }?.let { started ->
            inFlight.update { it - started.toSet() }
        }
        queued.forEach { (id, messages) ->
            val status = state[id]?.status
            when {
                status == null || status in ENDED -> {
                    queues.update { it - id }
                }

                status == AgentStatus.Idle && id !in flying && messages.isNotEmpty() -> {
                    handOver(core, id, messages)
                }

                else -> {}
            }
        }
    }

    // A prompt that cannot be sent (the target stopped in between) has nowhere to go; it must not
    // leave the target marked as busy forever.
    @Suppress("TooGenericExceptionCaught")
    private fun handOver(
        core: OctoCore,
        id: TaskId,
        messages: List<String>,
    ) {
        queues.update { it - id }
        inFlight.update { it + id }
        scope.launch {
            try {
                core.sendPrompt(id, messages.joinToString("\n\n---\n\n"))
            } catch (e: CancellationException) {
                throw e
            } catch (ignored: Exception) {
                inFlight.update { it - id }
            }
        }
    }

    companion object {
        const val SERVER_NAME = "octo"
        const val MAX_MESSAGE = 20_000
        const val MAX_QUEUED = 20
        const val MAX_TITLE = 200
        private val MODEL = Regex("[A-Za-z0-9][A-Za-z0-9._:/@\\[\\]-]{0,99}")

        // A plain subset of git ref names and commit ids; git still rejects the rest of its own rules.
        private val REF = Regex("[A-Za-z0-9_][A-Za-z0-9._/-]{0,199}")

        /** Below the five-minute idle timeout HTTP MCP clients apply to a silent tool call. */
        const val MAX_WAIT_MS = 240_000L
        private val ENDED = setOf(AgentStatus.Stopped, AgentStatus.Failed)

        // Uuid.random() draws from a cryptographically secure generator on every platform.
        @OptIn(ExperimentalUuidApi::class)
        private fun randomToken(): String = Uuid.random().toHexString() + Uuid.random().toHexString()
    }
}
