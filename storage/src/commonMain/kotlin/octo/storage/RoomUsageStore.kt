package octo.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import octo.core.agent.AgentId
import octo.core.io.ReadOnlyFileException
import octo.core.usage.UsageRecord
import octo.core.usage.UsageStore

/** Usage records appended row by row, so a turn costs its own records rather than the whole log. */
internal class RoomUsageStore(
    private val db: OctoDatabase,
    initial: List<UsageRecord>,
    override val warning: String?,
) : UsageStore {
    private val lock = Mutex()
    private val mutable = MutableStateFlow(initial)
    override val records: StateFlow<List<UsageRecord>> = mutable.asStateFlow()

    override suspend fun append(
        records: List<UsageRecord>,
        keep: Int,
    ) = lock.withLock {
        warning?.let { throw ReadOnlyFileException(it) }
        db.write {
            db.usage().insert(records.map { it.toRow() })
            db.usage().keepNewest(keep)
        }
        mutable.value = (mutable.value + records).takeLast(keep)
    }
}

internal fun UsageRecord.toRow() =
    UsageRow(
        atMs = atMs,
        agent = agent.value,
        model = model,
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        cacheReadTokens = cacheReadTokens,
        cacheWriteTokens = cacheWriteTokens,
    )

internal fun UsageRow.toRecord() =
    UsageRecord(atMs, AgentId(agent), model, inputTokens, outputTokens, cacheReadTokens, cacheWriteTokens)
