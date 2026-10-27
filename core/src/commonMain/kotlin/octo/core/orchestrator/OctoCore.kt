package octo.core.orchestrator

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import octo.core.agent.AgentId
import octo.core.agent.PermissionMode
import octo.core.agent.RequestResponse
import octo.core.git.TaskDiff
import octo.core.process.ProcessUsage
import octo.core.task.Layout
import octo.core.task.NewTask
import octo.core.task.OctoState
import octo.core.task.TaskId
import octo.core.task.TranscriptEntry

/** The only surface the UI uses; flow-based so a socket transport can serve it later. */
@Suppress(
    // Being the only surface, it grows with every screen; splitting it would give the UI a second door.
    "TooManyFunctions",
)
interface OctoCore {
    val state: StateFlow<OctoState>

    fun transcript(taskId: TaskId): Flow<List<TranscriptEntry>>

    /**
     * Creates the worktree and branch and starts the agent. Throws
     * [octo.core.terms.TermsNotAcknowledgedException] before touching the repository when the
     * agent's terms are still pending.
     */
    suspend fun createTask(request: NewTask): TaskId

    /** Branches of [repo] on [machine] (null for this computer) that a new task can start from. */
    suspend fun branches(
        repo: String,
        machine: String?,
    ): List<String>

    /**
     * Sends [text] to an idle agent. A busy agent is steered when its adapter can take the text
     * into the running turn; otherwise the text waits in [octo.core.task.Task.queue] and is sent
     * once the agent is idle.
     */
    suspend fun sendPrompt(
        taskId: TaskId,
        text: String,
    )

    /** Replaces the text of a prompt that is still queued; throws when it was already sent. */
    suspend fun editQueued(
        taskId: TaskId,
        queuedId: Int,
        text: String,
    )

    /** Drops a queued prompt; one that was already sent is left alone. */
    suspend fun removeQueued(
        taskId: TaskId,
        queuedId: Int,
    )

    suspend fun respond(
        taskId: TaskId,
        requestId: String,
        response: RequestResponse,
    )

    /**
     * A new task on a new branch that starts from this task's worktree as it is now, uncommitted
     * and untracked changes included, with the same agent and transcript. The agent forks its
     * own session when it can; otherwise the next prompt carries the conversation over.
     */
    suspend fun forkTask(taskId: TaskId): TaskId

    /**
     * Moves the task to [agent], optionally on [model]; the worktree and branch stay. The same
     * agent switches its model in place when it can. Otherwise a fresh session starts and the
     * next prompt goes out with a handoff built from the transcript.
     */
    suspend fun switchAgent(
        taskId: TaskId,
        agent: AgentId,
        model: String?,
    )

    /**
     * Keeps [mode] on the task and switches a running agent to it; an agent that cannot switch in
     * place takes it on its next start, and one without the mode runs Supervised.
     */
    suspend fun setPermissionMode(
        taskId: TaskId,
        mode: PermissionMode,
    )

    /** Cancels the current turn; the agent keeps running. */
    suspend fun cancel(taskId: TaskId)

    /** Ends the agent process. */
    suspend fun stop(taskId: TaskId)

    /** Resumes the agent's own session when the adapter can, else starts a fresh one and says so. */
    suspend fun restart(taskId: TaskId)

    /**
     * Stops the agent and forgets the task. The branch is always kept; a dirty worktree is only
     * deleted with [force], otherwise [WorktreeDirtyException] is thrown and nothing changes.
     */
    suspend fun removeTask(
        taskId: TaskId,
        deleteWorktree: Boolean,
        force: Boolean = false,
    )

    suspend fun diff(taskId: TaskId): TaskDiff

    suspend fun updateLayout(layout: Layout)

    /** Clears the "done" mark once the user has looked at the task. */
    suspend fun markSeen(taskId: TaskId)

    /**
     * CPU and memory of each running agent on this computer, sampled every few seconds while
     * collected; a task on a remote machine or without a known process is left out.
     */
    val processUsage: Flow<Map<TaskId, ProcessUsage>>
}

class UnknownTaskException(
    id: TaskId,
) : IllegalArgumentException("No task '${id.value}'")

class UnknownAgentException(
    id: AgentId,
) : IllegalArgumentException("Agent '${id.value}' is not available")

class TaskNotRunningException(
    id: TaskId,
) : IllegalStateException("Task '${id.value}' has no running agent")

class WorktreeDirtyException(
    id: TaskId,
    val worktree: String,
) : IllegalStateException("The worktree of task '${id.value}' has uncommitted changes: $worktree")
