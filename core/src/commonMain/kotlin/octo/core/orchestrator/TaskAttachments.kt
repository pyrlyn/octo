package octo.core.orchestrator

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import octo.core.agent.AgentSession
import octo.core.agent.PromptImage
import octo.core.agent.RequestResponse
import octo.core.attachments.AttachmentStore
import octo.core.task.Attachment
import octo.core.task.NewAttachment
import octo.core.task.Task
import octo.core.task.TaskId
import octo.core.task.TranscriptEntry
import kotlin.coroutines.cancellation.CancellationException

/**
 * Files the user attaches to a task's messages: saved on the task's machine, named to the agent by
 * path, and deleted with the messages they belong to. The orchestrator holds the task lock.
 */
internal class TaskAttachments(
    private val machines: Machines,
    private val transcripts: MutableStateFlow<Map<TaskId, List<TranscriptEntry>>>,
) {
    // A file saved before a later one failed would never be referenced, so it goes too.
    @Suppress("TooGenericExceptionCaught")
    suspend fun save(
        task: Task,
        files: List<NewAttachment>,
    ): List<Attachment> {
        if (files.isEmpty()) return emptyList()
        val store = store(task) ?: error("Files cannot be attached to tasks on ${where(task.machine)}")
        files.forEach { require(it.bytes.size <= NewAttachment.MAX_BYTES) { "\"${it.name}\" is too large" } }
        val saved = mutableListOf<Attachment>()
        try {
            files.forEach { saved += Attachment(it.name, store.save(task.id, it.name, it.bytes), it.mimeType) }
        } catch (e: Exception) {
            if (e !is CancellationException) quietly { store.delete(saved.map { it.path }) }
            throw e
        }
        return saved
    }

    /**
     * Saves [files] and hands them to [send]. They are saved first, so a prompt that waits in the
     * queue already points at them; whatever stops the send, they are deleted again.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun sending(
        task: Task,
        files: List<NewAttachment>,
        send: suspend (List<Attachment>) -> Unit,
    ) {
        val saved = save(task, files)
        try {
            send(saved)
        } catch (e: Exception) {
            if (e !is CancellationException) drop(task, saved)
            throw e
        }
    }

    /**
     * Sends [response] with the paths of the files saved for each question, then shows the files on
     * the request's transcript entry. Files go only with answers.
     */
    suspend fun respond(
        task: Task,
        session: AgentSession,
        requestId: String,
        response: RequestResponse,
        files: Map<String, List<NewAttachment>>,
    ) {
        if (files.values.all { it.isEmpty() }) return session.respond(requestId, response)
        require(response is RequestResponse.Answers) { "Only answers to questions can carry files" }
        val saved = files.mapValues { (_, list) -> save(task, list) }
        val answers =
            response.answers +
                saved.map { (question, list) ->
                    question to withPaths(response.answers[question].orEmpty(), list).trimStart()
                }
        session.respond(requestId, RequestResponse.Answers(answers))
        answered(task.id, requestId, saved.values.flatten())
    }

    /** The pictures among [attachments], read back from the task's machine; one that cannot be read keeps only its path. */
    suspend fun images(
        task: Task,
        attachments: List<Attachment>,
    ): List<PromptImage> {
        val store = store(task) ?: return emptyList()
        return attachments.filter { it.isImage }.mapNotNull { image ->
            quietly { PromptImage(image.path, image.mimeType, store.read(image.path)) }
        }
    }

    suspend fun drop(
        task: Task,
        attachments: List<Attachment>,
    ) {
        if (attachments.isNotEmpty()) quietly { store(task)?.delete(attachments.map { it.path }) }
    }

    suspend fun dropAll(task: Task) {
        quietly { store(task)?.deleteAll(task.id) }
    }

    /**
     * Deletes the files of the messages after the checkpoint on [ref], which a revert discarded,
     * and takes them off those messages.
     */
    suspend fun dropAfter(
        task: Task,
        ref: String,
    ) {
        val entries = transcripts.value[task.id].orEmpty()
        val at = entries.indexOfFirst { it is TranscriptEntry.Checkpoint && it.ref == ref }
        if (at < 0) return
        val discarded = entries.drop(at + 1).flatMap { it.attachments }
        if (discarded.isEmpty()) return
        drop(task, discarded)
        val gone = discarded.toSet()
        transcripts.update { all ->
            all + (task.id to all[task.id].orEmpty().map { it.without(gone) })
        }
    }

    private fun answered(
        id: TaskId,
        requestId: String,
        attachments: List<Attachment>,
    ) {
        if (attachments.isEmpty()) return
        transcripts.update { all ->
            val entries =
                all[id].orEmpty().map {
                    if (it is TranscriptEntry.Request && it.id == requestId) it.copy(attachments = attachments) else it
                }
            all + (id to entries)
        }
    }

    private fun store(task: Task): AttachmentStore? = machines[task.machine].attachments

    // An attachment left behind only takes space in the task's folder, never the task down.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun <T> quietly(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    companion object {
        /** [text] followed by the paths of [attachments] on the task's machine, which every agent gets. */
        fun withPaths(
            text: String,
            attachments: List<Attachment>,
        ): String {
            if (attachments.isEmpty()) return text
            val list = attachments.joinToString("\n") { "- ${it.path}" }
            return "$text\n\nAttached files, saved on the machine you run on:\n$list"
        }
    }
}

private val TranscriptEntry.attachments: List<Attachment>
    get() =
        when (this) {
            is TranscriptEntry.Text -> attachments
            is TranscriptEntry.Request -> attachments
            else -> emptyList()
        }

private fun TranscriptEntry.without(gone: Set<Attachment>): TranscriptEntry =
    when (this) {
        is TranscriptEntry.Text -> copy(attachments = attachments - gone)
        is TranscriptEntry.Request -> copy(attachments = attachments - gone)
        else -> this
    }
