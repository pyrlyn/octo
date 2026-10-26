package octo.core.io

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString

/** A text file replaced as a whole: a crash mid-write leaves the old content, never half a file. */
class AtomicFile(
    val path: Path,
    private val fs: FileSystem = SystemFileSystem,
) {
    /** Null when the file does not exist yet. */
    suspend fun read(): String? =
        withContext(Dispatchers.IO) {
            if (!fs.exists(path)) return@withContext null
            fs.source(path).buffered().use { it.readString() }
        }

    suspend fun write(text: String) {
        withContext(Dispatchers.IO) {
            path.parent?.let { fs.createDirectories(it) }
            val temp = Path("$path.tmp")
            fs.sink(temp).buffered().use { it.writeString(text) }
            fs.atomicMove(temp, path)
        }
    }
}
