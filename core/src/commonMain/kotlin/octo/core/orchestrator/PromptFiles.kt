package octo.core.orchestrator

import octo.core.agent.Capability
import octo.core.attachments.Attachment
import octo.core.attachments.AttachmentStore
import octo.core.attachments.Attachments
import octo.core.attachments.FileUpload
import octo.core.attachments.PromptImage
import octo.core.task.Task
import kotlin.coroutines.cancellation.CancellationException

/** The files of a task's messages, kept in the attachment store of the machine the task runs on. */
internal class PromptFiles(
    private val store: (Task) -> AttachmentStore,
    private val newId: () -> String,
) {
    /** Saves [files] in one new folder of [task]; throws before writing anything when they break the limits. */
    suspend fun save(
        task: Task,
        files: List<FileUpload>,
    ): List<Attachment> {
        if (files.isEmpty()) return emptyList()
        Attachments.check(files)
        val store = store(task)
        val batch = newId()
        return files.zip(Attachments.names(files)) { file, name ->
            val path = store.save(task.id, batch, name, file.bytes)
            Attachment(name, path, Attachments.mimeType(file.bytes), file.bytes.size.toLong())
        }
    }

    /** What the agent gets: the images it takes natively, and the text with the paths of the rest. */
    suspend fun message(
        task: Task,
        text: String,
        attachments: List<Attachment>,
        capabilities: Set<Capability>,
    ): Pair<String, List<PromptImage>> {
        val images = if (Capability.Images in capabilities) images(task, attachments) else emptyList()
        val rest = attachments.filter { a -> images.none { it.path == a.path } }
        return Attachments.withPaths(text, rest.map { it.path }) to images
    }

    // An image that cannot be read back still reaches the agent, as a path.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun images(
        task: Task,
        attachments: List<Attachment>,
    ): List<PromptImage> =
        attachments.filter { it.sendsAsImage && !it.removed }.mapNotNull {
            try {
                PromptImage(it.path, it.mimeType, store(task).read(it.path))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }

    // A file left behind costs only space, and removing the task tries its whole folder again.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    suspend fun discard(
        task: Task,
        attachments: List<Attachment>,
    ) {
        for (attachment in attachments.filterNot { it.removed }) {
            try {
                store(task).delete(attachment.path)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Nothing to do; see above.
            }
        }
    }

    /** Deletes every file of [task]; a machine that is gone or unreachable keeps them. */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    suspend fun forget(task: Task) {
        try {
            store(task).deleteAll(task.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The task goes anyway; its files are only space on that machine.
        }
    }
}
