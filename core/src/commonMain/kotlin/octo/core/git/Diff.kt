package octo.core.git

/** A task's changes against the merge base of its base ref, including untracked files. */
data class TaskDiff(
    val baseRef: String,
    val mergeBase: String,
    val files: List<FileDiff>,
)

enum class FileChange { Added, Deleted, Modified, Renamed }

data class FileDiff(
    val path: String,
    val oldPath: String?,
    val change: FileChange,
    val binary: Boolean,
    val hunks: List<Hunk>,
) {
    val additions: Int get() = hunks.sumOf { hunk -> hunk.lines.count { it.kind == LineKind.Added } }
    val deletions: Int get() = hunks.sumOf { hunk -> hunk.lines.count { it.kind == LineKind.Removed } }
}

data class Hunk(
    val oldStart: Int,
    val oldCount: Int,
    val newStart: Int,
    val newCount: Int,
    /** Text after the closing `@@`, usually the enclosing function. */
    val section: String,
    val lines: List<DiffLine>,
)

enum class LineKind { Context, Added, Removed }

data class DiffLine(
    val kind: LineKind,
    val text: String,
)

/** Parses `git diff` output. Repository content is untrusted, so malformed input is skipped, never thrown on. */
object DiffParser {
    fun parse(text: String): List<FileDiff> {
        val files = mutableListOf<FileBuilder>()
        for (line in text.lineSequence()) {
            if (line.startsWith(DIFF_HEADER)) {
                files += FileBuilder(headerPath(line.removePrefix(DIFF_HEADER)))
            } else {
                files.lastOrNull()?.accept(line)
            }
        }
        return files.mapNotNull { it.build() }
    }

    // `a/<p> b/<p>` is ambiguous when <p> has spaces; both halves are equal unless the file moved.
    private fun headerPath(rest: String): String? {
        val half = (rest.length - 1) / 2
        val old = rest.take(half)
        val new = rest.drop(half + 1)
        return if (old.startsWith("a/") && new.startsWith("b/") && old.drop(2) == new.drop(2)) new.drop(2) else null
    }

    private const val DIFF_HEADER = "diff --git "
}

private class FileBuilder(
    private var path: String?,
) {
    private var oldPath: String? = null
    private var change = FileChange.Modified
    private var binary = false
    private val hunks = mutableListOf<HunkBuilder>()

    fun accept(line: String) {
        when {
            hunks.lastOrNull()?.accept(line) == true -> Unit
            line.startsWith("@@") -> HunkBuilder.parse(line)?.let { hunks += it }
            line.startsWith("--- ") -> side(line.removePrefix("--- "), "a/")?.let { if (path == null) path = it }
            line.startsWith("+++ ") -> side(line.removePrefix("+++ "), "b/")?.let { path = it }
            else -> header(line)
        }
    }

    private fun header(line: String) {
        when {
            line.startsWith("new file mode") -> change = FileChange.Added
            line.startsWith("deleted file mode") -> change = FileChange.Deleted
            line.startsWith("rename from ") -> renamedFrom(line.removePrefix("rename from "))
            line.startsWith("rename to ") -> path = line.removePrefix("rename to ")
            line.startsWith("Binary files ") || line == "GIT binary patch" -> binary = true
        }
    }

    private fun renamedFrom(from: String) {
        oldPath = from
        change = FileChange.Renamed
    }

    // `/dev/null` stands for the missing side of an added or deleted file.
    private fun side(
        value: String,
        prefix: String,
    ): String? = value.substringBefore('\t').takeIf { it.startsWith(prefix) }?.removePrefix(prefix)

    fun build(): FileDiff? = path?.let { FileDiff(it, oldPath, change, binary, hunks.map(HunkBuilder::build)) }
}

private class HunkBuilder(
    private val oldStart: Int,
    private val oldCount: Int,
    private val newStart: Int,
    private val newCount: Int,
    private val section: String,
) {
    private var oldLeft = oldCount
    private var newLeft = newCount
    private val lines = mutableListOf<DiffLine>()

    /** Takes a body line while the header's counts say the hunk is not finished. */
    fun accept(line: String): Boolean {
        val open = oldLeft > 0 || newLeft > 0
        val first = line.firstOrNull()
        val kind =
            when (first) {
                '+' -> LineKind.Added
                '-' -> LineKind.Removed
                ' ', null -> LineKind.Context
                else -> null
            }
        val body = open && kind != null
        if (body) add(kind, line.drop(1))
        // "\ No newline at end of file" belongs to the hunk but carries no content.
        return body || first == '\\'
    }

    private fun add(
        kind: LineKind,
        text: String,
    ) {
        if (kind != LineKind.Added) oldLeft--
        if (kind != LineKind.Removed) newLeft--
        lines += DiffLine(kind, text)
    }

    fun build() = Hunk(oldStart, oldCount, newStart, newCount, section, lines.toList())

    companion object {
        private val HEADER = Regex("""^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@ ?(.*)$""")

        fun parse(line: String): HunkBuilder? =
            HEADER.find(line)?.groupValues?.let { g ->
                HunkBuilder(
                    oldStart = g[1].toInt(),
                    oldCount = g[2].toIntOrNull() ?: 1,
                    newStart = g[3].toInt(),
                    newCount = g[4].toIntOrNull() ?: 1,
                    section = g[5],
                )
            }
    }
}
