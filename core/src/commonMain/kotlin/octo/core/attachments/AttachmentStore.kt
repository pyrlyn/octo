package octo.core.attachments

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemPathSeparator
import kotlinx.io.readByteArray
import kotlinx.io.write
import octo.core.process.ProcessLauncher
import octo.core.process.ProcessSpec
import octo.core.process.run
import octo.core.task.TaskId
import kotlin.io.encoding.Base64

/**
 * Where a machine keeps the files attached to its tasks' messages: one folder per task, one
 * folder per message inside it, so removing the task removes them all.
 */
interface AttachmentStore {
    /** Writes [bytes] as [name] in the [batch] folder of [task]; returns the file's path on the machine. */
    suspend fun save(
        task: TaskId,
        batch: String,
        name: String,
        bytes: ByteArray,
    ): String

    suspend fun read(path: String): ByteArray

    /** Deletes one saved file; a file that is gone already is fine. */
    suspend fun delete(path: String)

    /** Deletes every file of [task]. */
    suspend fun deleteAll(task: TaskId)
}

/** The folder a path from a saved transcript must be under before anything deletes or reads it. */
private fun requireInside(
    root: String,
    path: String,
    separator: Char = '/',
) {
    val inside = path.startsWith("$root$separator") && path.split(separator).none { it == ".." }
    require(inside) { "'$path' is not an Octo attachment" }
}

/** Attachments on this computer, under [root] in the Octo home. */
class LocalAttachments(
    private val root: Path,
    private val fs: FileSystem = SystemFileSystem,
) : AttachmentStore {
    override suspend fun save(
        task: TaskId,
        batch: String,
        name: String,
        bytes: ByteArray,
    ): String =
        withContext(Dispatchers.IO) {
            val dir = Path(root, task.value, batch)
            fs.createDirectories(dir)
            val file = Path(dir, name)
            fs.sink(file).buffered().use { it.write(bytes) }
            file.toString()
        }

    override suspend fun read(path: String): ByteArray =
        withContext(Dispatchers.IO) {
            requireInside(root.toString(), path, SystemPathSeparator)
            fs.source(Path(path)).buffered().use { it.readByteArray() }
        }

    override suspend fun delete(path: String) {
        withContext(Dispatchers.IO) {
            requireInside(root.toString(), path, SystemPathSeparator)
            fs.delete(Path(path), mustExist = false)
        }
    }

    override suspend fun deleteAll(task: TaskId) {
        withContext(Dispatchers.IO) { deleteTree(Path(root, task.value)) }
    }

    private fun deleteTree(path: Path) {
        if (!fs.exists(path)) return
        if (fs.metadataOrNull(path)?.isDirectory == true) fs.list(path).forEach(::deleteTree)
        fs.delete(path, mustExist = false)
    }
}

/**
 * Attachments on a POSIX machine reached through [launcher]: bytes travel as base64 over the
 * command's stdin and stdout, so they need nothing on the machine but `sh` and `base64`.
 */
class RemoteAttachments(
    private val launcher: ProcessLauncher,
    /** `<home>/.octo/attachments` there, looked up on first use. */
    private val root: suspend () -> String,
) : AttachmentStore {
    override suspend fun save(
        task: TaskId,
        batch: String,
        name: String,
        bytes: ByteArray,
    ): String {
        val path = "${root()}/${task.value}/$batch/$name"
        val data = Base64.encode(bytes).chunked(LINE).joinToString("\n")
        sh("mkdir -p \"\$(dirname \"\$1\")\" && base64 -d > \"\$1\"", path, stdin = data)
        return path
    }

    override suspend fun read(path: String): ByteArray {
        requireInside(root(), path)
        return Base64.decode(sh("base64 < \"\$1\"", path).filterNot { it.isWhitespace() })
    }

    override suspend fun delete(path: String) {
        requireInside(root(), path)
        sh("rm -f -- \"\$1\"", path)
    }

    override suspend fun deleteAll(task: TaskId) {
        sh("rm -rf -- \"\$1\"", "${root()}/${task.value}")
    }

    // The path goes in as an argument, never into the script, so no name can break out of it.
    private suspend fun sh(
        script: String,
        path: String,
        stdin: String? = null,
    ): String {
        val result = launcher.run(ProcessSpec("sh", listOf("-c", script, "sh", path)), stdin)
        check(result.exitCode == 0) { "Attachment command failed on the remote machine: ${result.stderr.trim()}" }
        return result.stdout
    }

    private companion object {
        // Long lines keep the number of writes down; base64 -d takes any line length.
        const val LINE = 64 * 1024
    }
}
