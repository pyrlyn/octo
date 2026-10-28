package octo.core.attachments

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import octo.core.process.ProcessLauncher
import octo.core.process.ProcessSpec
import octo.core.process.run
import octo.core.task.TaskId
import kotlin.io.encoding.Base64
import kotlin.random.Random

/** One machine's attachment folders: one flat folder per task, under a root in the Octo home there. */
interface AttachmentStore {
    /** Saves [bytes] under a unique file name made from [name]; returns its path on the machine. */
    suspend fun save(
        task: TaskId,
        name: String,
        bytes: ByteArray,
    ): String

    suspend fun read(path: String): ByteArray

    /** Deletes those of [paths] that lie in an attachment folder; any other path is left alone. */
    suspend fun delete(paths: List<String>)

    /** Deletes [task]'s folder with everything in it. */
    suspend fun deleteAll(task: TaskId)
}

/** This computer's attachments, under [root]. */
class LocalAttachmentStore(
    private val root: Path,
    private val fs: FileSystem = SystemFileSystem,
) : AttachmentStore {
    override suspend fun save(
        task: TaskId,
        name: String,
        bytes: ByteArray,
    ) = withContext(Dispatchers.IO) {
        val dir = Path(root, folder(task))
        fs.createDirectories(dir)
        val path = Path(dir, fileName(name))
        fs.sink(path).buffered().use { it.write(bytes) }
        path.toString()
    }

    override suspend fun read(path: String) =
        withContext(Dispatchers.IO) {
            require(owns(path)) { "'$path' is not an attachment" }
            fs.source(Path(path)).buffered().use { it.readByteArray() }
        }

    override suspend fun delete(paths: List<String>) {
        withContext(Dispatchers.IO) { paths.filter(::owns).forEach { fs.delete(Path(it), mustExist = false) } }
    }

    override suspend fun deleteAll(task: TaskId) {
        withContext(Dispatchers.IO) {
            val dir = Path(root, folder(task))
            if (!fs.exists(dir)) return@withContext
            fs.list(dir).forEach { fs.delete(it, mustExist = false) }
            fs.delete(dir, mustExist = false)
        }
    }

    // Paths come back from the saved state, which anyone can edit; only files Octo wrote are touched.
    private fun owns(path: String) = ".." !in path && Path(path).parent?.parent?.toString() == root.toString()
}

/**
 * A POSIX machine's attachments, reached through [launcher] (ssh), under the directory [root]
 * returns. The bytes cross the line-framed stdio as base64.
 */
class RemoteAttachmentStore(
    private val launcher: ProcessLauncher,
    private val root: suspend () -> String,
) : AttachmentStore {
    override suspend fun save(
        task: TaskId,
        name: String,
        bytes: ByteArray,
    ): String {
        val dir = "${root()}/${folder(task)}"
        val path = "$dir/${fileName(name)}"
        sh("""mkdir -p "$1" && base64 -d > "$2"""", listOf(dir, path), stdin = Base64.Mime.encode(bytes))
        return path
    }

    override suspend fun read(path: String): ByteArray {
        require(owns(path)) { "'$path' is not an attachment" }
        val encoded = sh("""base64 < "$1"""", listOf(path))
        return Base64.decode(encoded.filterNot { it.isWhitespace() })
    }

    override suspend fun delete(paths: List<String>) {
        val owned = paths.filter { owns(it) }
        if (owned.isNotEmpty()) sh("""rm -f -- "$@"""", owned)
    }

    override suspend fun deleteAll(task: TaskId) {
        sh("""rm -rf -- "$1"""", listOf("${root()}/${folder(task)}"))
    }

    private suspend fun owns(path: String) =
        ".." !in path && path.substringBeforeLast('/').substringBeforeLast('/') == root()

    private suspend fun sh(
        script: String,
        args: List<String>,
        stdin: String? = null,
    ): String {
        val result = launcher.run(ProcessSpec("sh", listOf("-c", script, "sh") + args), stdin)
        check(result.exitCode == 0) { result.stderr.trim().ifEmpty { "'$script' failed with code ${result.exitCode}" } }
        return result.stdout
    }
}

private val TASK_FOLDER = Regex("[A-Za-z0-9_-]+")
private val NOT_SAFE = Regex("[^A-Za-z0-9._-]+")
private const val NAME_LENGTH = 80
private const val PREFIX_LENGTH = 8
private const val HEX = 16

// Task ids are Octo's own, but the state file they are read from is not trusted.
private fun folder(task: TaskId): String {
    require(TASK_FOLDER.matches(task.value)) { "Invalid task id '${task.value}'" }
    return task.value
}

/**
 * [name] reduced to characters every file system and shell takes, behind a random prefix so two
 * files with one name never collide.
 */
internal fun fileName(name: String): String {
    val safe =
        name
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(NOT_SAFE, "_")
            .trimStart('.', '-')
            .takeLast(NAME_LENGTH)
            .ifEmpty { "file" }
    val prefix =
        Random
            .nextInt()
            .toUInt()
            .toString(HEX)
            .padStart(PREFIX_LENGTH, '0')
    return "$prefix-$safe"
}
