package octo.ui.vm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import octo.core.agent.RequestResponse
import octo.core.orchestrator.OctoCore
import octo.core.orchestrator.WorktreeDirtyException
import octo.core.status.AgentStatus
import octo.core.task.PendingRequest
import octo.core.task.SidebarStatus
import octo.core.task.Task
import octo.core.task.TaskId
import octo.core.task.TaskView
import octo.core.task.TranscriptEntry

sealed interface RemoveStep {
    /** Asks whether to keep the worktree. */
    data object Confirm : RemoveStep

    /** The worktree has uncommitted changes; deleting it loses them. */
    data class Dirty(
        val worktree: String,
    ) : RemoveStep
}

data class TaskUiState(
    val task: Task? = null,
    val status: AgentStatus = AgentStatus.Stopped,
    val failure: String? = null,
    val transcript: List<TranscriptEntry> = emptyList(),
    val pending: List<PendingRequest> = emptyList(),
    val draft: String = "",
    /** Answers being filled in, by request id and then question id. */
    val answers: Map<String, Map<String, List<String>>> = emptyMap(),
    val remove: RemoveStep? = null,
    val error: String? = null,
) {
    /** The task was removed while the screen was open. */
    val gone: Boolean get() = task == null

    /** Turns are marked seen while the screen is open, so this never shows "done". */
    val sidebarStatus: SidebarStatus get() = task?.let { TaskView(it, status).sidebarStatus } ?: SidebarStatus.Stopped

    val canSend: Boolean get() = status == AgentStatus.Idle && draft.isNotBlank()
    val canCancel: Boolean get() = status == AgentStatus.Working || status == AgentStatus.Blocked
    val canStop: Boolean get() = status != AgentStatus.Stopped && status != AgentStatus.Failed
    val canRestart: Boolean get() = !gone && !canStop
}

/** One task: its live transcript, the requests it waits on, the prompt box and its lifecycle. */
class TaskViewModel(
    private val core: OctoCore,
    val taskId: TaskId,
    parent: CoroutineScope,
) {
    private val scope = parent.childScope()
    private val mutable = MutableStateFlow(TaskUiState().withCore(core.state.value[taskId], emptyList()))
    val state: StateFlow<TaskUiState> = mutable.asStateFlow()

    init {
        act {
            val transcript = core.transcript(taskId).onStart { emit(emptyList()) }
            combine(core.state, transcript, ::Pair).collect { (octo, entries) ->
                val view = octo[taskId]
                mutable.update { it.withCore(view, entries) }
                // The screen is open, so a finished turn is seen as soon as it lands.
                if (view?.unseen == true) core.markSeen(taskId)
            }
        }
    }

    fun editDraft(text: String) = mutable.update { it.copy(draft = text) }

    fun send() {
        val text =
            state.value
                .takeIf { it.canSend }
                ?.draft
                ?.trim() ?: return
        mutable.update { it.copy(draft = "") }
        act({ mutable.update { it.copy(draft = text) } }) { core.sendPrompt(taskId, text) }
    }

    fun choose(
        requestId: String,
        optionId: String,
    ) = act { core.respond(taskId, requestId, RequestResponse.Option(optionId)) }

    /** Sets a free-text answer, picks a single-select option or toggles a multi-select one. */
    fun answer(
        requestId: String,
        questionId: String,
        value: String,
        toggle: Boolean = false,
    ) = mutable.update { l ->
        val request = l.answers[requestId].orEmpty()
        val current = request[questionId].orEmpty()
        val next =
            when {
                !toggle -> listOf(value)
                value in current -> current - value
                else -> current + value
            }
        l.copy(answers = l.answers + (requestId to request + (questionId to next)))
    }

    fun submitAnswers(requestId: String) {
        val answers =
            state.value.answers[requestId]
                .orEmpty()
                .mapValues { (_, values) -> values.joinToString(RequestResponse.Answers.SEPARATOR) }
        act {
            core.respond(taskId, requestId, RequestResponse.Answers(answers))
            mutable.update { it.copy(answers = it.answers - requestId) }
        }
    }

    fun dismiss(requestId: String) = act { core.respond(taskId, requestId, RequestResponse.Cancelled) }

    fun cancel() = act { core.cancel(taskId) }

    fun stop() = act { core.stop(taskId) }

    fun restart() = act { core.restart(taskId) }

    fun requestRemove() = mutable.update { it.copy(remove = RemoveStep.Confirm) }

    fun cancelRemove() = mutable.update { it.copy(remove = null) }

    /** Removes the task; a dirty worktree turns into a second, explicit question. */
    fun remove(deleteWorktree: Boolean) =
        act {
            try {
                core.removeTask(taskId, deleteWorktree)
                mutable.update { it.copy(remove = null) }
            } catch (e: WorktreeDirtyException) {
                mutable.update { it.copy(remove = RemoveStep.Dirty(e.worktree)) }
            }
        }

    fun forceRemove() =
        act {
            core.removeTask(taskId, deleteWorktree = true, force = true)
            mutable.update { it.copy(remove = null) }
        }

    fun dismissError() = mutable.update { it.copy(error = null) }

    fun close() = scope.cancel()

    private fun act(
        undo: () -> Unit = {},
        block: suspend () -> Unit,
    ) = scope.action({ e ->
        undo()
        mutable.update { it.copy(error = e.userMessage) }
    }, block)
}

private fun TaskUiState.withCore(
    view: TaskView?,
    transcript: List<TranscriptEntry>,
) = copy(
    task = view?.task,
    status = view?.status ?: AgentStatus.Stopped,
    failure = view?.failure,
    transcript = transcript,
    pending = view?.pending.orEmpty(),
)
