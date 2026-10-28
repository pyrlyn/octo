package octo.core.task

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import octo.core.agent.AgentId
import octo.core.agent.AgentSessionRef
import octo.core.agent.PermissionMode
import octo.core.agent.PermissionOption
import octo.core.agent.Question
import octo.core.agent.SessionStats
import octo.core.status.AgentStatus
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class TaskId(
    val value: String,
)

/** What the user asks for when creating a task. */
data class NewTask(
    /** Any directory inside the repository. */
    val repo: String,
    val baseRef: String,
    val agent: AgentId,
    val title: String,
    val prompt: String? = null,
    /** Adapter options such as the model. */
    val options: Map<String, String> = emptyMap(),
    /** A machine id from the config; null runs the task on this computer. */
    val machine: String? = null,
    /** Null takes the default from the config. */
    val permissionMode: PermissionMode? = null,
    val group: TaskGroup? = null,
)

/** One agent working in its own worktree; this part is persisted. */
@Serializable
data class Task(
    val id: TaskId,
    val title: String,
    val repo: String,
    val worktree: String,
    val branch: String,
    val baseRef: String,
    val agent: AgentId,
    val options: Map<String, String> = emptyMap(),
    /** The agent's own session, so a restart can resume the conversation. */
    val sessionRef: AgentSessionRef? = null,
    val lastStatus: AgentStatus = AgentStatus.Stopped,
    /** Null for this computer; optional so state files from before remote machines still load. */
    val machine: String? = null,
    /** The agent this task was switched away from; the next prompt carries the conversation over. */
    val handoffFrom: String? = null,
    /** The task this one was forked from. */
    val parentId: TaskId? = null,
    /** What the user chose; the agent runs it only if its adapter has it. */
    val permissionMode: PermissionMode = PermissionMode.Supervised,
    /** Prompts waiting for the agent to be idle, oldest first; persisted so a restart keeps them. */
    val queue: List<QueuedPrompt> = emptyList(),
    /** Tasks started together to compare agents on one prompt. */
    val group: TaskGroup? = null,
    /** Where the sidebar lists the task; only shown while the `sections` flag is on. */
    val place: TaskPlace = TaskPlace.Active,
    /** Snapshots of the worktree, oldest first; recorded only while the `checkpoints` flag is on. */
    val checkpoints: List<Checkpoint> = emptyList(),
    /** Files were reverted but the agent's conversation was not; the next prompt tells it so. */
    val revertNote: String? = null,
)

/** The worktree's files at one moment, kept as a commit on [ref] in the task's repository. */
@Serializable
data class Checkpoint(
    val id: Int,
    val ref: String,
    /**
     * The agent run that was live when this was recorded. A revert rolls the agent's conversation
     * back only over checkpoints of the run still live; null once the two no longer line up.
     */
    val run: String? = null,
)

/** The sidebar section a task sits in. */
@Serializable
sealed interface TaskPlace {
    @Serializable
    @SerialName("active")
    data object Active : TaskPlace

    @Serializable
    @SerialName("pinned")
    data object Pinned : TaskPlace

    /**
     * Out of the way until [untilMs] (epoch milliseconds), or with null until the agent's turn
     * ends. A task that blocks or fails wakes either way: it cannot go on without the user.
     */
    @Serializable
    @SerialName("snoozed")
    data class Snoozed(
        val untilMs: Long? = null,
    ) : TaskPlace

    /** Finished with, such as after its pull request merged; the worktree stays until removed. */
    @Serializable
    @SerialName("settled")
    data object Settled : TaskPlace
}

/** Tasks started together from one prompt; the sidebar lists them under [title]. */
@Serializable
data class TaskGroup(
    val id: String,
    val title: String,
)

/** A prompt the user sent while the agent was busy and could not take it into the turn. */
@Serializable
data class QueuedPrompt(
    val id: Int,
    val text: String,
)

/** A request the agent is waiting on. */
sealed interface PendingRequest {
    val id: String

    data class Permission(
        override val id: String,
        val title: String,
        val options: List<PermissionOption>,
    ) : PendingRequest

    data class Questions(
        override val id: String,
        val questions: List<Question>,
    ) : PendingRequest
}

data class TaskView(
    val task: Task,
    val status: AgentStatus,
    val pending: List<PendingRequest> = emptyList(),
    val failure: String? = null,
    /** A turn finished since the user last looked at the task. */
    val unseen: Boolean = false,
    /** Figures from the running agent; cleared on every start, never persisted. */
    val stats: SessionStats = SessionStats(),
    /** Set when the agent hit its usage limit, until the next turn starts; never persisted. */
    val limit: UsageLimit? = null,
) {
    val sidebarStatus: SidebarStatus
        get() =
            when (status) {
                AgentStatus.Blocked -> SidebarStatus.Blocked
                AgentStatus.Failed -> SidebarStatus.Failed
                AgentStatus.Working -> SidebarStatus.Working
                AgentStatus.Starting -> SidebarStatus.Starting
                AgentStatus.Idle -> if (unseen) SidebarStatus.Done else SidebarStatus.Idle
                AgentStatus.Stopped -> SidebarStatus.Stopped
                AgentStatus.Queued -> SidebarStatus.Queued
            }
}

/** The agent's provider stopped taking work until [resetsAtMs] (epoch milliseconds), or until an unknown time. */
data class UsageLimit(
    val resetsAtMs: Long?,
)

/** Ordered by priority: the first one present wins a rollup. */
enum class SidebarStatus { Blocked, Failed, Working, Starting, Queued, Done, Idle, Stopped }

/** The status shown for a group of tasks, such as one repository; null for an empty group. */
fun Iterable<TaskView>.rollup(): SidebarStatus? = minOfOrNull { it.sidebarStatus }

/** Window layout, restored on restart. Fields are optional so older files keep loading. */
@Serializable
data class Layout(
    val selectedTask: TaskId? = null,
    val sidebarWidth: Float? = null,
    val diffOpen: Boolean = false,
)

data class OctoState(
    val tasks: List<TaskView> = emptyList(),
    val layout: Layout = Layout(),
    /** Problems found while loading state that the UI should mention once. */
    val warnings: List<String> = emptyList(),
) {
    operator fun get(id: TaskId): TaskView? = tasks.firstOrNull { it.task.id == id }

    /** This state with [id]'s view replaced by [change] of it; the same state when there is no such task. */
    internal fun change(
        id: TaskId,
        change: (TaskView) -> TaskView,
    ) = copy(tasks = tasks.map { if (it.task.id == id) change(it) else it })
}
