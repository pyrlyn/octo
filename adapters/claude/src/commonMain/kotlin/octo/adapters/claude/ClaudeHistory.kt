package octo.adapters.claude

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

/** Claude's session transcripts under `<config dir>/projects`, which carry each reply's token use. */
fun claudeHistory(configDir: Path): UsageHistory =
    JsonLinesHistory(
        listOf(Path(configDir, "projects")),
        accepts = { it.endsWith(".jsonl") },
        mayMatter = { "\"usage\"" in it },
        parse = ::claudeUsage,
    )

/**
 * One record per reply. A reply is written once per content block, and a resumed or forked
 * session copies earlier replies into its own file, so the message id makes each count once.
 */
internal fun claudeUsage(lines: Sequence<JsonObject>): List<HistoryEntry> =
    lines
        .filter { it.string("type") == "assistant" }
        .mapNotNull { line ->
            val message = line.obj("message") ?: return@mapNotNull null
            val usage = message.obj("usage") ?: return@mapNotNull null
            val at = isoMillis(line.string("timestamp")) ?: return@mapNotNull null
            val record =
                UsageRecord(
                    atMs = at,
                    agent = ClaudeAdapter.ID,
                    // Claude writes its own notices as replies from this placeholder model.
                    model = message.string("model")?.takeUnless { it == SYNTHETIC_MODEL },
                    inputTokens = usage.long("input_tokens") ?: 0,
                    outputTokens = usage.long("output_tokens") ?: 0,
                    cacheReadTokens = usage.long("cache_read_input_tokens") ?: 0,
                    cacheWriteTokens = usage.long("cache_creation_input_tokens") ?: 0,
                )
            HistoryEntry(record, message.string("id")).takeUnless { record.isEmpty }
        }.toList()

private const val SYNTHETIC_MODEL = "<synthetic>"
