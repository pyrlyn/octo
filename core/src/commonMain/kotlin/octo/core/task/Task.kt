package octo.core.task

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
    /** What the user chose; the agent runs it only if its adapter has it. */
    val permissionMode: PermissionMode = PermissionMode.Supervised,
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
            }
}

/** Ordered by priority: the first one present wins a rollup. */
enum class SidebarStatus { Blocked, Failed, Working, Starting, Done, Idle, Stopped }

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
}
