package octo.core.access

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import octo.core.agent.PermissionMode
import octo.core.agent.PermissionOption
import octo.core.agent.Question
import octo.core.status.AgentStatus
import octo.core.task.NewAttachment
import octo.core.task.SidebarStatus
import octo.core.task.TaskId
import octo.core.task.TranscriptEntry
import kotlin.io.encoding.Base64

// The bodies of [RemoteApi]'s calls, shared with its clients so both ends read the same shapes.

/** How [RemoteApi] and its clients read and write bodies; unknown keys let either end grow. */
val ApiJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

/** `POST /pair`: the one-time [code] from Devices and the device's [name]. */
@Serializable
data class PairBody(
    val code: String,
    val name: String,
)

/** The answer to a pairing: the bearer [token] for every later call. */
@Serializable
data class PairedBody(
    val token: String,
    val deviceId: String?,
)

/** `POST /tasks`: what a new task runs; mirrors [octo.core.task.NewTask]. */
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
    /** Files that go with [prompt]. */
    val attachments: List<AttachmentBody> = emptyList(),
)

/** A file sent with a prompt; [data] holds its bytes in base64. */
@Serializable
data class AttachmentBody(
    val name: String,
    val mimeType: String = "application/octet-stream",
    val data: String,
) {
    /** Throws [IllegalArgumentException] for a file the core would refuse, before any task is touched. */
    fun decode(): NewAttachment {
        require(name.isNotBlank()) { "Every attachment needs a name" }
        val bytes = Base64.decode(data)
        require(bytes.size <= NewAttachment.MAX_BYTES) { "\"$name\" is too large" }
        return NewAttachment(name, mimeType, bytes)
    }

    companion object {
        fun of(file: NewAttachment) = AttachmentBody(file.name, file.mimeType, Base64.encode(file.bytes))
    }
}

/** The id of a task `POST /tasks` created. */
@Serializable
data class CreatedBody(
    val id: TaskId,
)

/** `POST /tasks/{id}/prompt`: a message, with optional files. */
@Serializable
data class PromptBody(
    val text: String,
    val attachments: List<AttachmentBody> = emptyList(),
)

/**
 * `POST /tasks/{id}/switch`: hands the task to [agent]; a null [model] leaves the choice to the
 * agent. With [summary], the leaving agent writes a summary for the next one first.
 */
@Serializable
data class SwitchBody(
    val agent: String,
    val model: String? = null,
    val summary: Boolean = false,
)

/** `GET /tasks`. */
@Serializable
data class TaskListBody(
    val tasks: List<TaskBody>,
)

/** `GET /tasks/{id}/transcript`. */
@Serializable
data class TranscriptBody(
    val entries: List<TranscriptEntry>,
)

/** One task as the API shows it, with its open requests in [pending]. */
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

/** A request the agent is waiting on: a permission with [options], or [questions]. */
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

/** One of a guest's proposed messages and its [status]. */
@Serializable
data class GuestProposalBody(
    val id: String,
    val text: String,
    val status: ProposalStatus,
)

/** A message a guest proposes; it reaches the agent only once the owner sends it. */
@Serializable
data class ProposeBody(
    val text: String,
    val name: String = "",
)
