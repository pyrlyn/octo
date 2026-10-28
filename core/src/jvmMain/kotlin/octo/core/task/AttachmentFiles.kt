package octo.core.task

import java.io.File
import java.net.URLConnection
import java.nio.file.Files

/** Reads [file] from this computer's disk to attach it, as the window and the `octo` command both do. */
fun NewAttachment.Companion.read(file: File): NewAttachment {
    // Checked before reading, so a huge file is never pulled into memory.
    require(file.length() <= NewAttachment.MAX_BYTES) { "\"${file.name}\" is too large to attach" }
    val type =
        Files.probeContentType(file.toPath())
            ?: URLConnection.guessContentTypeFromName(file.name)
            ?: "application/octet-stream"
    return NewAttachment(file.name, type, file.readBytes())
}
