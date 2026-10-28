package octo.core.task

import kotlinx.serialization.Serializable

/** A file the user attached to a message, saved on the machine that runs the task. */
@Serializable
data class Attachment(
    val name: String,
    /** Where it is saved on the task's machine, which is where the agent reads it. */
    val path: String,
    val mimeType: String,
) {
    val isImage: Boolean get() = mimeType.startsWith("image/")
}

/** A file as the user hands it in, before Octo saves it on the task's machine. */
class NewAttachment(
    val name: String,
    val mimeType: String,
    val bytes: ByteArray,
) {
    companion object {
        /** The largest file Octo takes, so a stray video never fills the agent's machine. */
        const val MAX_BYTES = 20 * 1024 * 1024
    }
}
