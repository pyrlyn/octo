package octo.ui.vm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import octo.core.agent.AgentId
import octo.core.agent.AgentProbe
import octo.core.agent.AuthState
import octo.core.agent.RequestResponse
import octo.core.agent.VendorTerms
import octo.core.git.TaskDiff
import octo.core.orchestrator.AgentCatalog
import octo.core.orchestrator.AgentInfo
import octo.core.orchestrator.OctoCore
import octo.core.orchestrator.WorktreeDirtyException
import octo.core.status.AgentStatus
import octo.core.task.Layout
import octo.core.task.NewTask
import octo.core.task.OctoState
import octo.core.task.Task
import octo.core.task.TaskId
import octo.core.task.TaskView
import octo.core.task.TranscriptEntry
import octo.core.terms.TermsNotAcknowledgedException

/** Records what the view models ask of core and lets a test drive the state. */
class FakeOctoCore : OctoCore {
    override val state = MutableStateFlow(OctoState())
    val transcripts = MutableStateFlow<Map<TaskId, List<TranscriptEntry>>>(emptyMap())
    val calls = mutableListOf<String>()
    var termsPending: VendorTerms? = null
    var dirty = false
    var diffResult = TaskDiff("main", "abc", emptyList())
    private var next = 0

    fun put(view: TaskView) =
        state.update { s -> s.copy(tasks = s.tasks.filterNot { it.task.id == view.task.id } + view) }

    override fun transcript(taskId: TaskId): Flow<List<TranscriptEntry>> = transcripts.map { it[taskId].orEmpty() }

    override suspend fun createTask(request: NewTask): TaskId {
        termsPending?.let { throw TermsNotAcknowledgedException(request.agent, it) }
        calls += "create ${request.title} ${request.prompt}"
        val id = TaskId("t${++next}")
        put(TaskView(task(id, request.repo, request.title), AgentStatus.Starting))
        return id
    }

    override suspend fun sendPrompt(
        taskId: TaskId,
        text: String,
    ) {
        calls += "prompt $text"
    }

    override suspend fun respond(
        taskId: TaskId,
        requestId: String,
        response: RequestResponse,
    ) {
        calls += "respond $requestId $response"
    }

    override suspend fun cancel(taskId: TaskId) {
        calls += "cancel"
    }

    override suspend fun stop(taskId: TaskId) {
        calls += "stop"
    }

    override suspend fun restart(taskId: TaskId) {
        calls += "restart"
    }

    override suspend fun removeTask(
        taskId: TaskId,
        deleteWorktree: Boolean,
        force: Boolean,
    ) {
        if (deleteWorktree && dirty && !force) throw WorktreeDirtyException(taskId, "/wt")
        calls += "remove $deleteWorktree $force"
        state.update { s -> s.copy(tasks = s.tasks.filterNot { it.task.id == taskId }) }
    }

    override suspend fun diff(taskId: TaskId): TaskDiff = diffResult

    override suspend fun updateLayout(layout: Layout) {
        state.update { it.copy(layout = layout) }
    }

    override suspend fun markSeen(taskId: TaskId) {
        calls += "seen ${taskId.value}"
        state.update { s -> s.copy(tasks = s.tasks.map { if (it.task.id == taskId) it.copy(unseen = false) else it }) }
    }

    companion object {
        fun task(
            id: TaskId,
            repo: String = "/src/octo",
            title: String = "Task ${id.value}",
        ) = Task(id, title, repo, "/wt/${id.value}", "octo/${id.value}", "main", AgentId("demo"))
    }
}

class FakeCatalog(
    private val core: FakeOctoCore,
) : AgentCatalog {
    val acknowledged = mutableListOf<AgentId>()

    override suspend fun agents() =
        listOf(
            AgentInfo(AgentId("missing"), "Missing", emptySet(), AgentProbe(null, null, "1", AuthState.Unknown)),
            AgentInfo(AgentId("demo"), "Demo", emptySet(), AgentProbe("built-in", "1", "1", AuthState.LoggedIn)),
        )

    override suspend fun pendingTerms(id: AgentId) = core.termsPending

    override suspend fun acknowledgeTerms(id: AgentId) {
        acknowledged += id
        core.termsPending = null
    }
}
