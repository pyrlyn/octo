package octo.core.usage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.io.IOException
import octo.core.agent.SessionStats
import octo.core.io.AtomicFile
import octo.core.io.JsonListFile
import octo.core.io.ReadOnlyFileException
import octo.core.status.AgentStatus
import octo.core.task.OctoState
import octo.core.task.TaskId
import kotlin.time.Clock

/** The tokens Octo saw each task's agent use, kept in `usage.json` so totals outlive the session. */
class UsageLog private constructor(
    private val file: JsonListFile<UsageRecord>,
) {
    /** Set when `usage.json` could not be read; nothing is recorded until it is fixed or removed. */
    val warning: String? get() = file.warning

    val records: StateFlow<List<UsageRecord>> get() = file.items

    suspend fun add(records: List<UsageRecord>) {
        if (records.isNotEmpty()) file.change { (it + records).takeLast(MAX_RECORDS) }
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
        /** The oldest records go first, so the file stays small enough to rewrite on every turn. */
        const val MAX_RECORDS = 10_000

        suspend fun open(file: AtomicFile) = UsageLog(JsonListFile.open(file, UsageRecord.serializer(), "records"))
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
