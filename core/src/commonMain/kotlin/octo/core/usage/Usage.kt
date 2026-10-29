package octo.core.usage

import kotlinx.serialization.Serializable
import octo.core.agent.AgentId

/** Tokens one agent used with one model, at [atMs] (epoch milliseconds). */
@Serializable
data class UsageRecord(
    val atMs: Long,
    val agent: AgentId,
    val model: String? = null,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val cacheReadTokens: Long = 0,
    val cacheWriteTokens: Long = 0,
) {
    val tokens: Long get() = inputTokens + outputTokens + cacheReadTokens + cacheWriteTokens

    val isEmpty: Boolean get() =
        inputTokens == 0L && outputTokens == 0L && cacheReadTokens == 0L &&
            cacheWriteTokens == 0L
}

/** An agent's own record of the tokens it used, read from the files it keeps on this computer. */
fun interface UsageHistory {
    /** Every record found; a file or line that cannot be read is skipped. */
    suspend fun read(): List<UsageRecord>
}

/** Where a total was counted from. */
enum class UsageSource { Octo, Agent }

data class UsageTotal(
    val agent: AgentId,
    val model: String?,
    val source: UsageSource,
    val inputTokens: Long,
    val outputTokens: Long,
    val cacheReadTokens: Long,
    val cacheWriteTokens: Long,
) {
    val tokens: Long get() = inputTokens + outputTokens + cacheReadTokens + cacheWriteTokens
}

/** Totals per agent and model since a moment, largest first, with the histories that could not be read. */
data class UsageSummary(
    val totals: List<UsageTotal> = emptyList(),
    val warnings: List<String> = emptyList(),
    /** Tokens per time bucket, when the caller asked for buckets. */
    val series: List<Long> = emptyList(),
)

/** Tokens per bucket; [edges] are the ascending bucket starts and a record before the first is left out. */
internal fun series(
    records: List<UsageRecord>,
    edges: List<Long>,
): List<Long> {
    val sums = LongArray(edges.size)
    for (record in records) {
        val found = edges.binarySearch(record.atMs)
        // A miss gives -(insertion point) - 1; the bucket is the edge just before that point.
        val bucket = if (found >= 0) found else -found - 2
        if (bucket >= 0) sums[bucket] += record.tokens
    }
    return sums.toList()
}

internal fun summarize(
    records: List<UsageRecord>,
    source: UsageSource,
    sinceMs: Long?,
): List<UsageTotal> =
    records
        .filter { sinceMs == null || it.atMs >= sinceMs }
        .groupBy { it.agent to it.model }
        .map { (key, group) ->
            UsageTotal(
                agent = key.first,
                model = key.second,
                source = source,
                inputTokens = group.sumOf { it.inputTokens },
                outputTokens = group.sumOf { it.outputTokens },
                cacheReadTokens = group.sumOf { it.cacheReadTokens },
                cacheWriteTokens = group.sumOf { it.cacheWriteTokens },
            )
        }
