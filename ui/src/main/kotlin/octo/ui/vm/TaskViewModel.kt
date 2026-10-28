package octo.ui.vm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import octo.core.agent.PermissionMode
import octo.core.agent.RequestResponse
import octo.core.orchestrator.OctoCore
import octo.core.orchestrator.TaskNotRunningException
import octo.core.orchestrator.WorktreeDirtyException
import octo.core.status.AgentStatus
import octo.core.task.NewAttachment
import octo.core.task.PendingRequest
import octo.core.task.QueuedPrompt
import octo.core.task.SidebarStatus
import octo.core.task.Task
import octo.core.task.TaskId
import octo.core.task.TaskView
import octo.core.task.TranscriptEntry
import octo.core.task.UsageLimit

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
    /** Files going out with the draft. */
    val attachments: List<NewAttachment> = emptyList(),
    /** Prompts waiting for the agent, oldest first. */
    val queue: List<QueuedPrompt> = emptyList(),
    /** A queued prompt being rewritten; its text is the edit in progress. */
    val editing: QueuedPrompt? = null,
    /** Answers being filled in, by request id and then question id. */
    val answers: Map<String, Map<String, List<String>>> = emptyMap(),
    /** Files going out with a free-text answer, by request id and then question id. */
    val answerFiles: Map<String, Map<String, List<NewAttachment>>> = emptyMap(),
    val remove: RemoveStep? = null,
    val error: String? = null,
    /** The agent hit its usage limit; always null while the `usage` flag is off. */
    val limit: UsageLimit? = null,
    /** How full the agent's context is; always null while the `usage` flag is off. */
    val context: ContextSummary? = null,
    /** Refs of the checkpoints the task can go back to; always empty while the `checkpoints` flag is off. */
    val revertable: Set<String> = emptySet(),
    /** A revert waiting for the user to confirm it. */
    val reverting: TranscriptEntry.Checkpoint? = null,
) {
    /** The task was removed while the screen was open. */
    val gone: Boolean get() = task == null

    /** Turns are marked seen while the screen is open, so this never shows "done". */
    val sidebarStatus: SidebarStatus get() = task?.let { TaskView(it, status).sidebarStatus } ?: SidebarStatus.Stopped

    /** A busy or queued agent still takes text: the core steers the turn or queues it. */
    val canSend: Boolean get() = status in LIVE && (draft.isNotBlank() || attachments.isNotEmpty())

    val busy: Boolean get() = status == AgentStatus.Working || status == AgentStatus.Blocked
    val canCancel: Boolean get() = status == AgentStatus.Working || status == AgentStatus.Blocked
    val canStop: Boolean get() = status != AgentStatus.Stopped && status != AgentStatus.Failed
    val canRestart: Boolean get() = !gone && !canStop

    /** Waiting for a free slot, which the user may skip. */
    val canStartNow: Boolean get() = status == AgentStatus.Queued

    private companion object {
        val LIVE =
            setOf(AgentStatus.Idle, AgentStatus.Starting, AgentStatus.Working, AgentStatus.Blocked, AgentStatus.Queued)
    }
}

// One small function per thing the task screen lets the user do; splitting them would split one screen.

/** One task: its live transcript, the requests it waits on, the prompt box and its lifecycle. */
@Suppress("TooManyFunctions")
class TaskViewModel(
    private val core: OctoCore,
    val taskId: TaskId,
    parent: CoroutineScope,
    /** False while the `usage` flag is off, which hides usage limits and the context popup. */
    private val limits: Boolean = false,
    /** False while the `checkpoints` flag is off, which hides reverting. */
    private val checkpoints: Boolean = false,
) {
    private val flags = Shown(limits, checkpoints)
    private val scope = parent.childScope()
    private val mutable = MutableStateFlow(TaskUiState().withCore(core.state.value[taskId], emptyList(), flags))
    val state: StateFlow<TaskUiState> = mutable.asStateFlow()

    init {
        act {
            val transcript = core.transcript(taskId).onStart { emit(emptyList()) }
            combine(core.state, transcript, ::Pair).collect { (octo, entries) ->
                val view = octo[taskId]
                mutable.update { it.withCore(view, entries, flags) }
                // The screen is open, so a finished turn is seen as soon as it lands.
                if (view?.unseen == true) core.markSeen(taskId)
            }
        }
    }

    fun editDraft(text: String) = mutable.update { it.copy(draft = text) }

    fun attach(files: List<NewAttachment>) = mutable.update { it.copy(attachments = it.attachments + files) }

    fun detach(index: Int) =
        mutable.update { it.copy(attachments = it.attachments.filterIndexed { i, _ -> i != index }) }

    fun send() {
        val ready = state.value.takeIf { it.canSend } ?: return
        val text = ready.draft.trim()
        val files = ready.attachments
        mutable.update { it.copy(draft = "", attachments = emptyList()) }
        act({ mutable.update { it.copy(draft = text, attachments = files) } }) { core.sendPrompt(taskId, text, files) }
    }

    fun editQueued(prompt: QueuedPrompt) = mutable.update { it.copy(editing = prompt) }

    fun editQueuedText(text: String) = mutable.update { it.copy(editing = it.editing?.copy(text = text)) }

    fun cancelQueuedEdit() = mutable.update { it.copy(editing = null) }

    fun saveQueued() {
        val edit = state.value.editing?.takeIf { it.text.isNotBlank() } ?: return
        act {
            core.editQueued(taskId, edit.id, edit.text.trim())
            mutable.update { it.copy(editing = null) }
        }
    }

    fun removeQueued(id: Int) =
        act {
            core.removeQueued(taskId, id)
            mutable.update { s -> s.copy(editing = s.editing?.takeIf { it.id != id }) }
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

    fun attachToAnswer(
        requestId: String,
        questionId: String,
        files: List<NewAttachment>,
    ) = changeAnswerFiles(requestId, questionId) { it + files }

    fun detachFromAnswer(
        requestId: String,
        questionId: String,
        index: Int,
    ) = changeAnswerFiles(requestId, questionId) { files -> files.filterIndexed { i, _ -> i != index } }

    private fun changeAnswerFiles(
        requestId: String,
        questionId: String,
        change: (List<NewAttachment>) -> List<NewAttachment>,
    ) = mutable.update { l ->
        val request = l.answerFiles[requestId].orEmpty()
        l.copy(
            answerFiles =
                l.answerFiles + (requestId to request + (questionId to change(request[questionId].orEmpty()))),
        )
    }

    fun submitAnswers(requestId: String) {
        val answers =
            state.value.answers[requestId]
                .orEmpty()
                .mapValues { (_, values) -> values.joinToString(RequestResponse.Answers.SEPARATOR) }
        val files = state.value.answerFiles[requestId].orEmpty()
        act {
            core.respond(taskId, requestId, RequestResponse.Answers(answers), files)
            mutable.update { it.copy(answers = it.answers - requestId, answerFiles = it.answerFiles - requestId) }
        }
    }

    fun dismiss(requestId: String) = act { core.respond(taskId, requestId, RequestResponse.Cancelled) }

    fun setPermissionMode(mode: PermissionMode) = act { core.setPermissionMode(taskId, mode) }

    fun cancel() = act { core.cancel(taskId) }

    fun stop() = act { core.stop(taskId) }

    fun restart() = act { core.restart(taskId) }

    fun startNow() = act { core.startNow(taskId) }

    /** Asks a limited agent to go on, starting it again first if it stopped. */
    fun resume() =
        act {
            try {
                core.sendPrompt(taskId, RESUME_PROMPT)
            } catch (_: TaskNotRunningException) {
                core.restart(taskId)
                core.sendPrompt(taskId, RESUME_PROMPT)
            }
        }

    /** Asks to confirm a revert to [checkpoint]; null drops the question. */
    fun askRevert(checkpoint: TranscriptEntry.Checkpoint?) = mutable.update { it.copy(reverting = checkpoint) }

    fun revert() {
        val checkpoint = state.value.reverting ?: return
        mutable.update { it.copy(reverting = null) }
        act { core.revert(taskId, checkpoint.ref) }
    }

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

    /** Shows a problem found outside the core, such as a file that could not be read. */
    fun showError(message: String) = mutable.update { it.copy(error = message) }

    fun dismissError() = mutable.update { it.copy(error = null) }

    fun close() = scope.cancel()

    private fun act(
        undo: () -> Unit = {},
        block: suspend () -> Unit,
    ) = scope.action({ e ->
        undo()
        mutable.update { it.copy(error = e.userMessage) }
    }, block)

    companion object {
        const val RESUME_PROMPT = "Continue"
    }
}

/** Which flag-gated parts of the task the screen shows. */
private data class Shown(
    val limits: Boolean,
    val checkpoints: Boolean,
)

private fun TaskUiState.withCore(
    view: TaskView?,
    transcript: List<TranscriptEntry>,
    shown: Shown,
) = copy(
    task = view?.task,
    status = view?.status ?: AgentStatus.Stopped,
    failure = view?.failure,
    transcript = transcript,
    pending = view?.pending.orEmpty(),
    queue = view?.task?.queue.orEmpty(),
    limit = view?.limit?.takeIf { shown.limits },
    context = view?.stats?.takeIf { shown.limits }?.let(::contextSummary),
    revertable =
        view
            ?.task
            ?.checkpoints
            ?.takeIf { shown.checkpoints }
            ?.map { it.ref }
            .orEmpty()
            .toSet(),
    // A prompt sent while it was being edited leaves nothing to save.
    editing =
        editing?.takeIf { edit ->
            view
                ?.task
                ?.queue
                .orEmpty()
                .any { it.id == edit.id }
        },
)
