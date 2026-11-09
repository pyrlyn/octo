package octo.core.usage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.io.IOException
import octo.core.agent.SessionStats
import octo.core.io.AtomicFile
import octo.core.io.JsonListFile
import octo.core.io.ListStore
import octo.core.io.ReadOnlyFileException
import octo.core.status.AgentStatus
import octo.core.task.OctoState
import octo.core.task.TaskId
import kotlin.time.Clock

/** Where usage records are kept between runs, oldest first. */
interface UsageStore {
    val records: StateFlow<List<UsageRecord>>

    /** Set when the stored records could not be read; every append is then refused with it. */
    val warning: String?

    /** Adds [records] at the end and drops the oldest past [keep]. */
    suspend fun append(
        records: List<UsageRecord>,
        keep: Int,
    )
}

/** The tokens Octo saw each task's agent use, kept so totals outlive the session. */
class UsageLog private constructor(
    private val store: UsageStore,
) {
    /** Set when the records could not be read; nothing is recorded until that is fixed. */
    val warning: String? get() = store.warning

    val records: StateFlow<List<UsageRecord>> get() = store.records

    suspend fun add(records: List<UsageRecord>) {
        if (records.isNotEmpty()) store.append(records, MAX_RECORDS)
    }

    /** Records how far each task's token totals grew whenever its agent is not mid-turn. */
    fun watch(
        state: StateFlow<OctoState>,
        scope: CoroutineScope,
        clock: Clock = Clock.System,
    ): Job {
        val recorder = UsageRecorder()
        return scope.launch {
            state.collect { octo ->
                val records = recorder.next(octo, clock.now().toEpochMilliseconds())
                // Fail open: a log that cannot be written costs the figures, never the session.
                try {
                    add(records)
                } catch (_: IOException) {
                } catch (_: ReadOnlyFileException) {
                }
            }
        }
    }

    companion object {
        /** The oldest records go first, so the log never grows without bound. */
        const val MAX_RECORDS = 10_000
        const val RECORDS_KEY = "records"

        fun open(store: UsageStore) = UsageLog(store)

        /** Records kept in `usage.json`, the format Octo used before SQLite. */
        suspend fun open(file: AtomicFile) =
            open(JsonListFile.open(file, UsageRecord.serializer(), RECORDS_KEY).asUsageStore())

        private fun ListStore<UsageRecord>.asUsageStore() =
            object : UsageStore {
                override val records = items
                override val warning = this@asUsageStore.warning

                override suspend fun append(
                    records: List<UsageRecord>,
                    keep: Int,
                ) = change { (it + records).takeLast(keep) }
            }
    }
}

/**
 * Turns session totals into records. Agents report totals for the session, which start again
 * from zero on every start, so a total below the last one recorded starts a new baseline.
 */
internal class UsageRecorder {
    private var recorded = emptyMap<TaskId, SessionStats>()

    fun next(
        state: OctoState,
        nowMs: Long,
    ): List<UsageRecord> {
        val records = mutableListOf<UsageRecord>()
        recorded =
            state.tasks.associate { view ->
                val now = view.stats
                val last = recorded[view.task.id]?.takeUnless { now.below(it) } ?: SessionStats()
                val id = view.task.id
                // Mid-turn figures arrive piecemeal; the turn is counted once it ends.
                if (view.status in MID_TURN) return@associate id to last
                val record =
                    UsageRecord(
                        atMs = nowMs,
                        agent = view.task.agent,
                        model = now.model,
                        inputTokens = now.inputTokens.orZero() - last.inputTokens.orZero(),
                        outputTokens = now.outputTokens.orZero() - last.outputTokens.orZero(),
                        cacheReadTokens = now.cacheReadTokens.orZero() - last.cacheReadTokens.orZero(),
                        cacheWriteTokens = now.cacheWriteTokens.orZero() - last.cacheWriteTokens.orZero(),
                    )
                if (!record.isEmpty) records += record
                id to now
            }
        return records
    }

    private fun SessionStats.below(other: SessionStats) =
        inputTokens.orZero() < other.inputTokens.orZero() ||
            outputTokens.orZero() < other.outputTokens.orZero() ||
            cacheReadTokens.orZero() < other.cacheReadTokens.orZero() ||
            cacheWriteTokens.orZero() < other.cacheWriteTokens.orZero()

    private fun Long?.orZero() = this ?: 0L

    private companion object {
        val MID_TURN = setOf(AgentStatus.Working, AgentStatus.Blocked)
    }
}
