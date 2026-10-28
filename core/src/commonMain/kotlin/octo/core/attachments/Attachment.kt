package octo.core.attachments

import kotlinx.serialization.Serializable

/** A file the user adds to a prompt or an answer, before it is saved on the task's machine. */
class FileUpload(
    val name: String,
    val bytes: ByteArray,
)

/** A file saved for a task; [path] is on the task's machine. */
@Serializable
data class Attachment(
    val name: String,
    val path: String,
    val mimeType: String,
    val size: Long,
    /** The file was deleted by a revert; the message keeps its name. */
    val removed: Boolean = false,
) {
    /** Small enough, and of a type, that agents with the image capability take it as an image. */
    val sendsAsImage: Boolean get() = mimeType in Attachments.IMAGE_TYPES && size <= Attachments.MAX_IMAGE_BYTES
}

/** An image a session sends natively; [path] lets an agent that turns out not to take images name it instead. */
class PromptImage(
    val path: String,
    val mimeType: String,
    val data: ByteArray,
)

object Attachments {
    const val MAX_FILES = 10
    const val MAX_FILE_BYTES = 20L * 1024 * 1024

    /** Claude's API takes no larger image. */
    const val MAX_IMAGE_BYTES = 5L * 1024 * 1024
    const val FALLBACK_TYPE = "application/octet-stream"
    private const val MAX_NAME = 100

    val IMAGE_TYPES = setOf("image/png", "image/jpeg", "image/gif", "image/webp")

    /** Throws when [files] break the limits, before anything is written. */
    fun check(files: List<FileUpload>) {
        require(files.size <= MAX_FILES) { "A message takes at most $MAX_FILES files" }
        files.forEach {
            require(it.bytes.size <= MAX_FILE_BYTES) { "'${it.name}' is larger than ${MAX_FILE_BYTES / MIB} MB" }
        }
    }

    /**
     * A file name that cannot leave its folder or upset a shell: the last segment only, with
     * anything unusual replaced. The name comes from the user, a paste or a remote client.
     */
    fun safeName(name: String): String {
        val last = name.substringAfterLast('/').substringAfterLast('\\')
        val clean = last.map { if (it.isLetterOrDigit() || it in "._-") it else '_' }.joinToString("")
        return clean.trimStart('.').take(MAX_NAME).ifEmpty { "file" }
    }

    /** Distinct [safeName]s for one message, in order. */
    fun names(files: List<FileUpload>): List<String> {
        val taken = mutableSetOf<String>()
        return files.map { file ->
            val name = safeName(file.name)
            val stem = name.substringBeforeLast('.')
            val ext = name.substring(stem.length)
            generateSequence(1) { it + 1 }
                .map { if (it == 1) name else "$stem-$it$ext" }
                .first { taken.add(it) }
        }
    }

    /** The type from the file's first bytes, so a renamed file is not sent as an image it is not. */
    fun mimeType(bytes: ByteArray): String =
        when {
            bytes.startsWith(PNG) -> "image/png"
            bytes.startsWith(JPEG) -> "image/jpeg"
            bytes.startsWith(GIF) -> "image/gif"
            bytes.startsWith(RIFF) && bytes.startsWith(WEBP, at = WEBP_AT) -> "image/webp"
            else -> FALLBACK_TYPE
        }

    /** What an agent reads about files it does not get natively; null when there are none. */
    fun pathsLine(paths: List<String>): String? =
        paths.takeIf { it.isNotEmpty() }?.joinToString("\n", "Attached files:\n") { "- $it" }

    /** [text] followed by the [pathsLine] of [paths]. */
    fun withPaths(
        text: String,
        paths: List<String>,
    ): String = listOfNotNull(text.takeIf { it.isNotBlank() }, pathsLine(paths)).joinToString("\n\n")

    private const val MIB = 1024 * 1024
    private const val WEBP_AT = 8
    private val PNG = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
    private val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    private val GIF = "GIF8".encodeToByteArray()
    private val RIFF = "RIFF".encodeToByteArray()
    private val WEBP = "WEBP".encodeToByteArray()

    private fun ByteArray.startsWith(
        prefix: ByteArray,
        at: Int = 0,
    ) = size >= at + prefix.size && prefix.indices.all { this[at + it] == prefix[it] }
}
