package octo.adapters.base

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readLine
import kotlinx.serialization.json.JsonObject
import octo.core.usage.UsageHistory
import octo.core.usage.UsageRecord
import kotlin.time.Instant

/** A record and, when the same one can appear in more than one file, the id that makes it count once. */
data class HistoryEntry(
    val record: UsageRecord,
    val key: String? = null,
)

/**
 * Usage read from an agent's JSON-lines files under [roots]. These logs only grow, so a file is
 * parsed again only when its size changed, which keeps reopening the Usage screen cheap.
 */
class JsonLinesHistory(
    private val roots: List<Path>,
    /** Which file names hold history. */
    private val accepts: (String) -> Boolean,
    /** Skips lines that cannot matter before paying for parsing them; they can be large. */
    private val mayMatter: (String) -> Boolean,
    /** One file's lines, in order. */
    private val parse: (Sequence<JsonObject>) -> List<HistoryEntry>,
    private val fs: FileSystem = SystemFileSystem,
) : UsageHistory {
    private val lock = Mutex()
    private var cache = emptyMap<Path, Pair<Long, List<HistoryEntry>>>()

    override suspend fun read(): List<UsageRecord> =
        withContext(Dispatchers.IO) {
            lock.withLock {
                cache =
                    roots.flatMap { files(it, MAX_DEPTH) }.associateWith { path ->
                        val size = fs.metadataOrNull(path)?.size ?: -1
                        cache[path]?.takeIf { it.first == size } ?: (size to parseFile(path))
                    }
                val seen = mutableSetOf<String>()
                cache.values
                    .flatMap { it.second }
                    .filter { entry -> entry.key?.let(seen::add) ?: true }
                    .map { it.record }
            }
        }

    private fun files(
        path: Path,
        depth: Int,
    ): List<Path> {
        val meta = fs.metadataOrNull(path) ?: return emptyList()
        return when {
            meta.isRegularFile -> listOf(path).filter { accepts(it.name) }
            meta.isDirectory && depth > 0 -> fs.list(path).flatMap { files(it, depth - 1) }
            else -> emptyList()
        }
    }

    // A file that vanishes or breaks mid-read counts as empty; the agent may be rotating it.
    private fun parseFile(path: Path): List<HistoryEntry> =
        try {
            fs.source(path).buffered().use { source ->
                parse(generateSequence { source.readLine() }.filter(mayMatter).mapNotNull(::parseJsonObject))
            }
        } catch (_: IOException) {
            emptyList()
        }

    private companion object {
        // Deep enough for Codex's sessions/YYYY/MM/DD; a link loop cannot recurse forever.
        const val MAX_DEPTH = 6
    }
}

/** An ISO-8601 instant in epoch milliseconds, or null when it is not one. */
fun isoMillis(text: String?): Long? =
    text?.let {
        try {
            Instant.parse(it).toEpochMilliseconds()
        } catch (_: IllegalArgumentException) {
            null
        }
    }

private const val MILLIS_PER_SECOND = 1_000L

// Before 2001-09-09 in milliseconds, but centuries ahead in seconds.
private const val MILLIS_FROM = 1_000_000_000_000L

/** A reset time agents give in epoch seconds or milliseconds, in milliseconds. */
fun epochMillis(value: Long): Long = if (value < MILLIS_FROM) value * MILLIS_PER_SECOND else value
