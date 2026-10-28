package octo.ui.vm

import octo.core.attachments.Attachments
import octo.core.attachments.FileUpload

/**
 * The files picked for one message that is not sent yet; [change] applies an edit to them in the
 * owner's state. They are checked against the limits as they are picked, so a send never fails
 * on them.
 */
class FilePicker internal constructor(
    private val change: ((List<FileUpload>) -> List<FileUpload>) -> Unit,
    private val onRejected: (String) -> Unit,
) {
    fun add(files: List<FileUpload>) =
        try {
            change { (it + files).also(Attachments::check) }
        } catch (e: IllegalArgumentException) {
            onRejected(e.message ?: "Those files cannot be attached")
        }

    fun remove(index: Int) = change { files -> files.filterIndexed { i, _ -> i != index } }

    /** A file the desktop could not read. */
    fun reject(reason: String) = onRejected(reason)
}
