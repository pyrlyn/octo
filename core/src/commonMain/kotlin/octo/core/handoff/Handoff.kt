package octo.core.handoff

import octo.core.agent.Role
import octo.core.task.TranscriptEntry

/**
 * What a fresh session is told when it takes a task over from another agent. It is built from
 * Octo's own transcript, not an agent-written summary, so it costs no turn and cannot drift from
 * what happened.
 */
object Handoff {
    /** Leaves most of a small context window for the new agent's own work. */
    const val DEFAULT_BUDGET_TOKENS = 12_000

    // A rough count is enough to stay inside a budget that is far below any context window.
    private const val CHARS_PER_TOKEN = 4

    /** A command's output keeps its end, where the outcome and errors are. */
    private const val OUTPUT_TAIL = 600

    fun tokens(text: String): Int = (text.length + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN

    /**
     * The handoff from [from] followed by [request]. The original request comes first, then the
     * most recent messages and command outcomes that fit in [budgetTokens]; [request] is never cut,
     * even when it alone is over the budget. Without a transcript, [request] goes out as it is.
     */
    fun build(
        entries: List<TranscriptEntry>,
        request: String,
        from: String,
        budgetTokens: Int = DEFAULT_BUDGET_TOKENS,
    ): String {
        val first = entries.indexOfFirst { it is TranscriptEntry.Text && it.role == Role.User && !it.partial }
        val original = (entries.getOrNull(first) as? TranscriptEntry.Text)?.text ?: return request
        val later = entries.drop(first + 1).mapNotNull(::line)
        val head =
            "You are taking over this task from $from. The conversation so far, from Octo's transcript:" +
                "\n\nOriginal request:\n"
        val tail = "\n\n$FOOTER\n\n$request"
        // The heading is reserved at its longest, so the count of left-out entries never tips the budget.
        var left = budgetTokens - tokens(head) - tokens(tail) - tokens(recentHeading(later.size))
        val kept = clip(original, left).also { left -= tokens(it) }
        val recent = ArrayDeque<String>()
        for (line in later.asReversed()) {
            val cost = tokens(line) + 1
            if (cost > left) break
            recent.addFirst(line)
            left -= cost
        }
        val omitted = later.size - recent.size
        return buildString {
            append(head).append(kept)
            if (later.isNotEmpty()) append(recentHeading(omitted)).append(recent.joinToString("\n"))
            append(tail)
        }
    }

    private fun recentHeading(omitted: Int) =
        "\n\nMost recent conversation" + (if (omitted > 0) " ($omitted earlier entries left out)" else "") + ":\n"

    private const val FOOTER =
        "The worktree and branch are the same; carry on from their current state. The new request:"

    /** Messages and command outcomes; thoughts, plans and notices do not carry over. */
    private fun line(entry: TranscriptEntry): String? =
        when (entry) {
            is TranscriptEntry.Text -> {
                if (entry.partial) null else (if (entry.role == Role.User) "User: " else "Agent: ") + entry.text
            }

            is TranscriptEntry.Tool -> {
                val outcome = "Tool: ${entry.title} (${entry.status.name.lowercase()})"
                entry.detail?.takeIf { it.isNotBlank() }?.let { "$outcome\n${it.takeLast(OUTPUT_TAIL)}" } ?: outcome
            }

            else -> {
                null
            }
        }

    private fun clip(
        text: String,
        tokens: Int,
    ): String {
        val chars = tokens.coerceAtLeast(0) * CHARS_PER_TOKEN
        return if (text.length <= chars) text else text.take((chars - CUT.length).coerceAtLeast(0)) + CUT
    }

    private const val CUT = " […]"
}
