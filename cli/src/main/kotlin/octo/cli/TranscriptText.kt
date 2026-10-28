package octo.cli

import octo.core.task.TranscriptEntry

/** A transcript as plain lines, one per entry, for reading in a terminal or grepping. */
object TranscriptText {
    fun line(entry: TranscriptEntry): String =
        when (entry) {
            is TranscriptEntry.Text -> {
                "${entry.role.name.lowercase()}: ${entry.text}"
            }

            is TranscriptEntry.Thought -> {
                "thought: ${entry.text}"
            }

            is TranscriptEntry.Tool -> {
                "tool: ${entry.title} (${entry.status.name.lowercase()})"
            }

            is TranscriptEntry.Plan -> {
                "plan: " +
                    entry.entries.joinToString("; ") { (if (it.done) "[x] " else "[ ] ") + it.text }
            }

            is TranscriptEntry.Request -> {
                "request: ${entry.title}" + if (entry.resolved) " (answered)" else " (open)"
            }

            is TranscriptEntry.Checkpoint -> {
                "checkpoint ${entry.id}"
            }

            is TranscriptEntry.Notice -> {
                (if (entry.error) "error: " else "notice: ") + entry.text
            }
        }
}
