package octo.feature.task

import octo.core.agent.SessionStats
import octo.feature.common.NONE
import octo.feature.common.label
import octo.feature.common.tokenCount
import java.util.Locale

enum class ContextKind(
    val label: String,
) {
    CacheRead("Cache read"),
    CacheWrite("Cache write"),
    Input("Input"),
    Output("Output"),
    Free("Free space"),
}

/** One part of the context and its share of the window, null when the window is unknown. */
data class ContextRow(
    val kind: ContextKind,
    val tokens: Long,
    val percent: Double?,
) {
    val tokensLabel: String get() = tokenCount(tokens)
    val percentLabel: String get() = percent?.let { String.format(Locale.ROOT, "%.1f%%", it) } ?: NONE
}

/** How full the agent's context window is, after its latest request. */
data class ContextSummary(
    val used: Long,
    val window: Long?,
    /** The parts the agent reported, then the free space. */
    val rows: List<ContextRow>,
) {
    val percent: Long? get() = window?.let { used * PERCENT / it }

    /** The short form for the button. */
    val label: String get() = percent?.let { "$it%" } ?: tokenCount(used)

    val usedLabel: String get() = window?.let { "${tokenCount(used)} / ${tokenCount(it)}" } ?: tokenCount(used)
}

/** Null until the agent reports how much of its context is used. */
internal fun contextSummary(stats: SessionStats): ContextSummary? {
    val used = stats.contextUsed ?: return null
    val window = stats.contextWindow?.takeIf { it > 0 }
    val parts = stats.contextParts
    val tokens =
        listOf(
            ContextKind.CacheRead to parts?.cacheRead,
            ContextKind.CacheWrite to parts?.cacheWrite,
            ContextKind.Input to parts?.input,
            ContextKind.Output to parts?.output,
            // An agent can go past its window just before it compacts.
            ContextKind.Free to window?.let { (it - used).coerceAtLeast(0) },
        )
    val rows =
        tokens.mapNotNull { (kind, count) ->
            count?.takeIf { it > 0 }?.let { ContextRow(kind, it, window?.let { w -> it * PERCENT.toDouble() / w }) }
        }
    return ContextSummary(used, window, rows)
}

private const val PERCENT = 100L
