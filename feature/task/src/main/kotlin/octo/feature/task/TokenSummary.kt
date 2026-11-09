package octo.feature.task

import octo.core.agent.SessionStats
import octo.feature.common.tokenCount
import java.util.Locale

/** What the session has sent to the model and received from it so far, and how fast the agent writes. */
data class TokenSummary(
    val sent: Long,
    val received: Long,
    val cacheRead: Long?,
    val cacheWrite: Long?,
    /** Output tokens per second over the running or latest turn; null until a turn has measured one. */
    val perSecond: Double?,
) {
    val sentLabel: String get() = tokenCount(sent)
    val receivedLabel: String get() = tokenCount(received)
    val speedLabel: String? get() = perSecond?.let { String.format(Locale.ROOT, "%.1f tok/s", it) }

    /** The one-line form under the composer. */
    val line: String
        get() = listOfNotNull("↑ $sentLabel sent", "↓ $receivedLabel received", speedLabel).joinToString("  ·  ")
}

/**
 * Null until the agent reports a count. Cache figures stay apart from [TokenSummary.sent]: agents disagree on
 * whether the input count already holds them, so adding them up would count some tokens twice.
 */
internal fun tokenSummary(
    stats: SessionStats,
    perSecond: Double?,
): TokenSummary? {
    if (stats.inputTokens == null && stats.outputTokens == null) return null
    return TokenSummary(
        sent = stats.inputTokens ?: 0,
        received = stats.outputTokens ?: 0,
        cacheRead = stats.cacheReadTokens,
        cacheWrite = stats.cacheWriteTokens,
        perSecond = perSecond,
    )
}

/**
 * Output tokens per second across a turn, from the running totals the agent reports. Some agents report only when
 * the turn ends, so the turn that just stopped is measured once more before it is closed.
 */
internal class TokenRate(
    private val now: () -> Long,
) {
    private var start: Long? = null
    private var base = 0L
    private var seen = 0L
    private var latest: Double? = null

    fun update(
        working: Boolean,
        output: Long?,
    ): Double? {
        val at = now()
        val count = output ?: seen
        // Measured up to the latest token rather than to now, so waiting on a tool does not drag the speed down.
        start?.let { begun ->
            val elapsed = at - begun
            if (count > seen && elapsed > 0) latest = (count - base) * MILLIS_PER_SECOND / elapsed
        }
        seen = count
        when {
            !working -> {
                start = null
            }

            start == null -> {
                start = at
                base = count
            }
        }
        return latest
    }
}

private const val MILLIS_PER_SECOND = 1000.0
