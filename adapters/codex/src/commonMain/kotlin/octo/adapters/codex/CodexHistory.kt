package octo.adapters.codex

import kotlinx.io.files.Path
import kotlinx.serialization.json.JsonObject
import octo.adapters.base.HistoryEntry
import octo.adapters.base.JsonLinesHistory
import octo.adapters.base.isoMillis
import octo.adapters.base.long
import octo.adapters.base.obj
import octo.adapters.base.string
import octo.core.usage.UsageHistory
import octo.core.usage.UsageRecord

/** Codex's session rollouts under `CODEX_HOME`, archived ones included, which log each turn's token totals. */
fun codexHistory(home: Path): UsageHistory =
    JsonLinesHistory(
        listOf(Path(home, "sessions"), Path(home, "archived_sessions")),
        accepts = { it.startsWith("rollout-") && it.endsWith(".jsonl") },
        mayMatter = { "\"token_count\"" in it || "\"turn_context\"" in it },
        parse = ::codexUsage,
    )

private data class Tokens(
    val input: Long = 0,
    val output: Long = 0,
    val cacheRead: Long = 0,
    val cacheWrite: Long = 0,
) {
    fun below(other: Tokens) =
        input < other.input || output < other.output || cacheRead < other.cacheRead || cacheWrite < other.cacheWrite

    operator fun minus(other: Tokens) =
        Tokens(input - other.input, output - other.output, cacheRead - other.cacheRead, cacheWrite - other.cacheWrite)
}

/**
 * One record per growth of the rollout's running totals, under the model of the turn it grew in.
 * Codex counts cached input inside `input_tokens`, so it is taken out to match the other agents.
 */
internal fun codexUsage(lines: Sequence<JsonObject>): List<HistoryEntry> {
    var model: String? = null
    var last = Tokens()
    val entries = mutableListOf<HistoryEntry>()
    for (line in lines) {
        if (line.string("type") == "turn_context") model = line.obj("payload")?.string("model") ?: model
        val (at, now) = totals(line) ?: continue
        val grown = now - if (now.below(last)) Tokens() else last
        last = now
        val record =
            UsageRecord(at, CodexAdapter.ID, model, grown.input, grown.output, grown.cacheRead, grown.cacheWrite)
        if (!record.isEmpty) entries += HistoryEntry(record)
    }
    return entries
}

/** When a `token_count` event was written and the session totals it carries. */
private fun totals(line: JsonObject): Pair<Long, Tokens>? {
    val payload =
        line.obj("payload")?.takeIf { line.string("type") == "event_msg" && it.string("type") == "token_count" }
    val total = payload?.obj("info")?.obj("total_token_usage")
    val at = isoMillis(line.string("timestamp"))
    if (total == null || at == null) return null
    val cached = total.long("cached_input_tokens") ?: 0
    return at to
        Tokens(
            input = (total.long("input_tokens") ?: 0) - cached,
            output = total.long("output_tokens") ?: 0,
            cacheRead = cached,
            cacheWrite = total.long("cache_write_input_tokens") ?: 0,
        )
}
