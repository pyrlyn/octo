package octo.core.task

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import octo.core.agent.AgentEvent
import octo.core.agent.PlanEntry
import octo.core.agent.Role
import octo.core.agent.StopReason
import octo.core.agent.ToolStatus
import octo.core.attachments.Attachment

/** One line of a task's history as the UI shows it. */
@Serializable
sealed interface TranscriptEntry {
    @Serializable
    @SerialName("text")
    data class Text(
        val role: Role,
        val text: String,
        /** Still streaming; never persisted. */
        val partial: Boolean = false,
        /** Files the user attached to this message. */
        val attachments: List<Attachment> = emptyList(),
    ) : TranscriptEntry

    @Serializable
    @SerialName("thought")
    data class Thought(
        val text: String,
    ) : TranscriptEntry

    @Serializable
    @SerialName("tool")
    data class Tool(
        val id: String,
        val title: String,
        val kind: String?,
        val status: ToolStatus,
        val detail: String? = null,
    ) : TranscriptEntry

    @Serializable
    @SerialName("plan")
    data class Plan(
        val entries: List<PlanEntry>,
    ) : TranscriptEntry

    @Serializable
    @SerialName("request")
    data class Request(
        val id: String,
        val title: String,
        val resolved: Boolean = false,
    ) : TranscriptEntry

    /** The worktree as it was here can be brought back; see [octo.core.task.Task.checkpoints]. */
    @Serializable
    @SerialName("checkpoint")
    data class Checkpoint(
        val id: Int,
        val ref: String,
    ) : TranscriptEntry

    @Serializable
    @SerialName("notice")
    data class Notice(
        val text: String,
        val error: Boolean = false,
    ) : TranscriptEntry
}

/** Folds agent events into a transcript. Pure, so the UI and persistence see the same history. */
object Transcript {
    const val MAX_ENTRIES = 500

    fun reduce(
        entries: List<TranscriptEntry>,
        event: AgentEvent,
    ): List<TranscriptEntry> = (fold(entries, event) ?: entries).takeLast(MAX_ENTRIES)

    fun add(
        entries: List<TranscriptEntry>,
        entry: TranscriptEntry,
    ): List<TranscriptEntry> = (entries + entry).takeLast(MAX_ENTRIES)

    private fun fold(
        entries: List<TranscriptEntry>,
        event: AgentEvent,
    ): List<TranscriptEntry>? =
        when (event) {
            is AgentEvent.MessageDelta -> {
                delta(entries, event)
            }

            is AgentEvent.Message -> {
                message(entries, event)
            }

            is AgentEvent.Thought -> {
                entries + TranscriptEntry.Thought(event.text)
            }

            is AgentEvent.ToolCall -> {
                tool(entries, event)
            }

            is AgentEvent.PlanUpdated -> {
                replaceLast<TranscriptEntry.Plan>(entries, TranscriptEntry.Plan(event.entries))
            }

            is AgentEvent.PermissionRequested -> {
                entries + TranscriptEntry.Request(event.id, event.title)
            }

            is AgentEvent.QuestionAsked -> {
                entries +
                    TranscriptEntry.Request(event.id, event.questions.joinToString(" ") { it.text })
            }

            is AgentEvent.RequestResolved -> {
                resolve(entries, event.id)
            }

            is AgentEvent.TurnEnded -> {
                turnEnded(entries, event)
            }

            is AgentEvent.Failed -> {
                entries + TranscriptEntry.Notice(event.message, error = true)
            }

            is AgentEvent.Exited -> {
                entries +
                    TranscriptEntry.Notice("Agent exited with code ${event.code}", error = event.code != 0)
            }

            else -> {
                null
            }
        }

    /** Drops what is still streaming; a restored transcript holds only finished entries. */
    fun settled(entries: List<TranscriptEntry>): List<TranscriptEntry> =
        entries.filterNot {
            it is TranscriptEntry.Text &&
                it.partial
        }

    private fun delta(
        entries: List<TranscriptEntry>,
        event: AgentEvent.MessageDelta,
    ): List<TranscriptEntry> {
        val last = entries.lastOrNull()
        return if (last is TranscriptEntry.Text && last.partial && last.role == event.role) {
            entries.dropLast(1) + last.copy(text = last.text + event.text)
        } else {
            entries + TranscriptEntry.Text(event.role, event.text, partial = true)
        }
    }

    private fun message(
        entries: List<TranscriptEntry>,
        event: AgentEvent.Message,
    ): List<TranscriptEntry> {
        val last = entries.lastOrNull()
        val streamed = last is TranscriptEntry.Text && last.partial && last.role == event.role
        return (if (streamed) entries.dropLast(1) else entries) + TranscriptEntry.Text(event.role, event.text)
    }

    private fun tool(
        entries: List<TranscriptEntry>,
        event: AgentEvent.ToolCall,
    ): List<TranscriptEntry> {
        val entry = TranscriptEntry.Tool(event.id, event.title, event.kind, event.status, event.detail)
        val index = entries.indexOfLast { it is TranscriptEntry.Tool && it.id == event.id }
        return if (index < 0) entries + entry else entries.toMutableList().also { it[index] = entry }
    }

    private inline fun <reified T : TranscriptEntry> replaceLast(
        entries: List<TranscriptEntry>,
        entry: T,
    ): List<TranscriptEntry> = if (entries.lastOrNull() is T) entries.dropLast(1) + entry else entries + entry

    private fun resolve(
        entries: List<TranscriptEntry>,
        id: String,
    ): List<TranscriptEntry> =
        entries.map { if (it is TranscriptEntry.Request && it.id == id) it.copy(resolved = true) else it }

    private fun turnEnded(
        entries: List<TranscriptEntry>,
        event: AgentEvent.TurnEnded,
    ): List<TranscriptEntry>? =
        when (event.stopReason) {
            StopReason.EndTurn -> {
                null
            }

            StopReason.Cancelled -> {
                entries + TranscriptEntry.Notice("Turn cancelled")
            }

            StopReason.MaxTurns -> {
                entries + TranscriptEntry.Notice("Turn stopped at the agent's turn limit")
            }

            StopReason.Refusal, StopReason.Error -> {
                entries + TranscriptEntry.Notice(event.message ?: "Turn ended: ${event.stopReason}", error = true)
            }
        }
}
