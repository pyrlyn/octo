package octo.core.persist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import octo.core.io.AtomicFile
import octo.core.task.Layout
import octo.core.task.Task
import octo.core.task.TranscriptEntry

@Serializable
data class PersistedTask(
    val task: Task,
    /** The tail of the transcript, bounded by [StateStore.TRANSCRIPT_TAIL]. */
    val transcript: List<TranscriptEntry> = emptyList(),
)

@Serializable
data class PersistedState(
    val version: Int = StateStore.VERSION,
    val tasks: List<PersistedTask> = emptyList(),
    val layout: Layout = Layout(),
)

data class LoadedState(
    val state: PersistedState,
    val warnings: List<String>,
)

/**
 * `state.json` with the last [BACKUPS] good files kept as `state.json.1..3`. Loading never fails:
 * a corrupt file is moved aside and the newest readable backup is used instead.
 */
class StateStore(
    private val path: Path,
    private val fs: FileSystem = SystemFileSystem,
) {
    private val lock = Mutex()

    suspend fun load(): LoadedState =
        lock.withLock {
            val warnings = mutableListOf<String>()
            for (candidate in candidates()) {
                when (val result = read(candidate)) {
                    is Read.Missing -> continue
                    is Read.Ok -> return@withLock LoadedState(result.state, warnings)
                    is Read.Broken -> warnings += quarantine(candidate, result.reason)
                }
            }
            LoadedState(PersistedState(), warnings)
        }

    suspend fun save(state: PersistedState) {
        lock.withLock {
            val text = json.encodeToString(PersistedState.serializer(), state)
            withContext(Dispatchers.IO) { rotate() }
            AtomicFile(path, fs).write(text)
        }
    }

    private fun candidates(): List<Path> = listOf(path) + (1..BACKUPS).map(::backup)

    private fun backup(n: Int) = Path("$path.$n")

    private suspend fun read(file: Path): Read {
        val text = AtomicFile(file, fs).read() ?: return Read.Missing
        return try {
            Read.Ok(json.decodeFromString(PersistedState.serializer(), text))
        } catch (e: SerializationException) {
            Read.Broken(e.message ?: "invalid JSON")
        } catch (e: IllegalArgumentException) {
            Read.Broken(e.message ?: "invalid state")
        }
    }

    // The broken file is kept for inspection rather than overwritten by the next save.
    private suspend fun quarantine(
        file: Path,
        reason: String,
    ): String =
        withContext(Dispatchers.IO) {
            val aside = Path("$file.corrupt")
            try {
                fs.atomicMove(file, aside)
                "Ignored a corrupt $file (moved to $aside): $reason"
            } catch (e: IOException) {
                "Ignored a corrupt $file (could not move it aside: ${e.message}): $reason"
            }
        }

    private fun rotate() {
        if (!fs.exists(path)) return
        for (n in BACKUPS downTo 2) {
            val older = backup(n - 1)
            if (fs.exists(older)) fs.atomicMove(older, backup(n))
        }
        fs.atomicMove(path, backup(1))
    }

    private sealed interface Read {
        data object Missing : Read

        data class Ok(
            val state: PersistedState,
        ) : Read

        data class Broken(
            val reason: String,
        ) : Read
    }

    companion object {
        const val VERSION = 1
        const val BACKUPS = 3
        const val TRANSCRIPT_TAIL = 200

        private val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
    }
}
