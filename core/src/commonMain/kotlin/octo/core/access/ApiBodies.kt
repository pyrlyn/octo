package octo.core.access

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import octo.core.agent.PermissionMode
import octo.core.agent.PermissionOption
import octo.core.agent.Question
import octo.core.status.AgentStatus
import octo.core.task.SidebarStatus
import octo.core.task.TaskId
import octo.core.task.TranscriptEntry

// The bodies of [RemoteApi]'s calls, shared with its clients so both ends read the same shapes.

/** How [RemoteApi] and its clients read and write bodies; unknown keys let either end grow. */
val ApiJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

@Serializable
data class PairBody(
    val code: String,
    val name: String,
)

@Serializable
data class PairedBody(
    val token: String,
    val deviceId: String?,
)

@Serializable
data class NewTaskBody(
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
data class CreatedBody(
    val id: TaskId,
)

@Serializable
data class PromptBody(
    val text: String,
)

@Serializable
data class TaskListBody(
    val tasks: List<TaskBody>,
)

@Serializable
data class TranscriptBody(
    val entries: List<TranscriptEntry>,
)

@Serializable
data class TaskBody(
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
    val pending: List<PendingBody>,
)

@Serializable
data class PendingBody(
    val id: String,
    val kind: String,
    val title: String? = null,
    val options: List<PermissionOption> = emptyList(),
    val questions: List<Question> = emptyList(),
)

/** What a guest sees of the one task their link opens. */
@Serializable
data class GuestBody(
    val title: String,
    val agent: String,
    val sidebarStatus: SidebarStatus,
    val entries: List<TranscriptEntry>,
    /** The guest's own messages and what the owner did with them. */
    val proposals: List<GuestProposalBody>,
)

@Serializable
data class GuestProposalBody(
    val id: String,
    val text: String,
    val status: ProposalStatus,
)

@Serializable
data class ProposeBody(
    val text: String,
    val name: String = "",
)
