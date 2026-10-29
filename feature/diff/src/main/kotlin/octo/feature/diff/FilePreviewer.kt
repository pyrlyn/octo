package octo.feature.diff

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import octo.core.process.ProcessLauncher
import octo.core.process.ProcessResult
import octo.core.process.ProcessSpec
import octo.core.process.run
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile
import kotlin.io.path.readBytes
import kotlin.io.path.readText

/**
 * Previews a file from a task's worktree. The file is whatever the agent or the repository put
 * there, so it is read only from inside the worktree and only up to [maxBytes].
 */
class FilePreviewer(
    private val launcher: ProcessLauncher,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
) {
    fun canPreview(path: String): Boolean = kind(path) != null

    /** Fails with a message for the user when the file cannot be shown. */
    suspend fun preview(
        worktree: String,
        path: String,
    ): Preview {
        val kind = requireNotNull(kind(path)) { "$path has no preview" }
        return withContext(io) {
            val file = resolve(worktree, path)
            when (kind) {
                Kind.Image -> Preview.Picture(Pictures.raster(file.readBytes()))
                Kind.Svg -> Preview.Picture(Pictures.svg(file.readBytes()))
                Kind.Notebook -> Notebooks.parse(file.readText())
                Kind.Archive -> archive(file)
                Kind.Database -> database(file)
                Kind.Mermaid -> mermaid(file)
            }
        }
    }

    internal fun resolve(
        worktree: String,
        path: String,
    ): Path {
        val root = Path(worktree).toRealPath()
        // Real paths, so a symlink in the repository cannot point the preview at files elsewhere.
        val file = root.resolve(path).toRealPath()
        require(file.startsWith(root) && file.isRegularFile()) { "$path is not a file in the worktree" }
        require(file.fileSize() <= maxBytes) { "$path is too large to preview" }
        return file
    }

    private fun archive(file: Path): Preview.Archive =
        // The central directory lists the entries without inflating any of them.
        ZipFile(file.toFile()).use { zip ->
            val entries =
                zip
                    .entries()
                    .asSequence()
                    .take(MAX_ENTRIES)
                    .map { Entry(it.name, it.size) }
                    .toList()
            Preview.Archive(entries, zip.size())
        }

    private suspend fun database(file: Path): Preview.Database {
        val header = file.inputStream().use { it.readNBytes(SQLITE_HEADER.size) }
        require(header.contentEquals(SQLITE_HEADER)) { "Not an SQLite database" }
        val sqlite = launcher.which("sqlite3") ?: error("Install sqlite3 to preview SQLite databases")
        val names = query(sqlite, file, "SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name")
        val tables =
            names.mapNotNull { it["name"] }.take(MAX_TABLES).map { name ->
                val rows = query(sqlite, file, "SELECT * FROM \"${name.replace("\"", "\"\"")}\" LIMIT $MAX_ROWS")
                Table(
                    name,
                    rows
                        .firstOrNull()
                        ?.keys
                        ?.toList()
                        .orEmpty(),
                    rows.map { it.values.toList() },
                )
            }
        return Preview.Database(tables)
    }

    /**
     * Raw SQL because the file is someone else's database read through the sqlite3 CLI; Octo's
     * own storage has no part in it. The database is untrusted: read-only, safe mode (no ATTACH,
     * extensions or file I/O) and no functions from its schema.
     */
    private suspend fun query(
        sqlite: String,
        file: Path,
        sql: String,
    ): List<Map<String, String>> {
        val args = listOf("-readonly", "-safe", "-json", "-cmd", "PRAGMA trusted_schema=OFF", file.toString(), sql)
        val result = runFor(SQLITE_TIMEOUT_MS, ProcessSpec(sqlite, args)) ?: error("sqlite3 took too long")
        check(result.exitCode == 0) { result.stderr.ifBlank { "sqlite3 failed" } }
        if (result.stdout.isBlank()) return emptyList()
        return Json.parseToJsonElement(result.stdout).jsonArray.map { row ->
            row.jsonObject.mapValues { (_, value) ->
                if (value is JsonNull) "NULL" else value.jsonPrimitive.content.take(MAX_CELL_CHARS)
            }
        }
    }

    private suspend fun mermaid(file: Path): Preview {
        val source = file.readText()
        val mmdc =
            launcher.which("mmdc") ?: return Preview.Source(source, "Install mermaid-cli (mmdc) to see the diagram.")
        val dir = createTempDirectory("octo-mermaid")
        try {
            // PNG rather than SVG: Mermaid puts labels in HTML, which Skia's SVG renderer skips.
            val png = dir.resolve("diagram.png")
            val result =
                runFor(MERMAID_TIMEOUT_MS, ProcessSpec(mmdc, listOf("-i", file.toString(), "-o", png.toString())))
            val drawn = result?.exitCode == 0 && png.exists()
            val why = result?.stderr?.lineSequence()?.lastOrNull { it.isNotBlank() } ?: "it took too long"
            return if (drawn) {
                Preview.Picture(Pictures.raster(png.readBytes()))
            } else {
                Preview.Source(source, "mmdc could not draw the diagram: $why")
            }
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    // Not withTimeout: its exception is a cancellation, which would leave the preview loading forever.
    private suspend fun runFor(
        timeoutMs: Long,
        spec: ProcessSpec,
    ): ProcessResult? = withTimeoutOrNull(timeoutMs) { launcher.run(spec) }

    internal enum class Kind { Image, Svg, Notebook, Archive, Database, Mermaid }

    companion object {
        const val DEFAULT_MAX_BYTES = 20L * 1024 * 1024
        private const val MAX_ENTRIES = 1000
        private const val MAX_TABLES = 20
        private const val MAX_ROWS = 50
        private const val MAX_CELL_CHARS = 200
        private const val SQLITE_TIMEOUT_MS = 10_000L
        private const val MERMAID_TIMEOUT_MS = 60_000L
        private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray()

        private val KINDS =
            buildMap {
                listOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "ico").forEach { put(it, Kind.Image) }
                put("svg", Kind.Svg)
                put("ipynb", Kind.Notebook)
                listOf("zip", "jar", "war", "aar", "apk", "whl", "vsix", "nupkg").forEach { put(it, Kind.Archive) }
                listOf("sqlite", "sqlite3", "db", "db3").forEach { put(it, Kind.Database) }
                listOf("mmd", "mermaid").forEach { put(it, Kind.Mermaid) }
            }

        internal fun kind(path: String): Kind? =
            KINDS[path.substringAfterLast('/').substringAfterLast('.', "").lowercase()]
    }
}
