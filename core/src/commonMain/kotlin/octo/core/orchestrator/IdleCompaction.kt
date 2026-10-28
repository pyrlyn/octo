package octo.core.orchestrator

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import octo.core.status.AgentStatus
import octo.core.task.TaskId
import octo.core.task.TaskPlace
import octo.core.task.TaskView
import kotlin.time.Clock

/**
 * Times the compaction of a large idle conversation to land just before its prompt cache expires:
 * the summary reads the context while the cache still holds it, so the turn after a long pause
 * does not pay to cache the whole context again. The times live in memory only.
 */
internal class IdleCompaction(
    private val clock: Clock,
    // Smaller contexts are cheap to cache again, and a summary loses detail.
    private val minTokens: Long,
) {
    private val due = MutableStateFlow<Map<TaskId, Long>>(emptyMap())

    // A compaction's own requests would otherwise set a time for another one.
    private val compacted = MutableStateFlow<Set<TaskId>>(emptySet())

    /** A request of [id]'s agent started just now, and the cache keeps its context for [ttlMs]. */
    fun cached(
        id: TaskId,
        ttlMs: Long,
    ) {
        if (id in compacted.value) return
        val at = clock.now().toEpochMilliseconds() + ttlMs - LEAD_MS
        due.update { it + (id to at) }
    }

    /** A prompt or a stop: nothing is due until the agent's next request. */
    fun cancel(id: TaskId) {
        due.update { it - id }
        compacted.update { it - id }
    }

    fun started(id: TaskId) = compacted.update { it + id }

    /** Hands each task to [compact] once its time comes; [compact] must not suspend this loop. */
    suspend fun run(compact: (TaskId) -> Unit) {
        due
            .map { it.values.minOrNull() }
            .distinctUntilChanged()
            .collectLatest { next ->
                next ?: return@collectLatest
                delay(next - clock.now().toEpochMilliseconds())
                val now = clock.now().toEpochMilliseconds()
                val ready = due.value.filterValues { it <= now }.keys
                due.update { it - ready }
                ready.forEach(compact)
            }
    }

    /** A snoozed or settled task is out of the user's way, so a pause there is no reason to pay. */
    fun worthIt(view: TaskView) =
        view.status == AgentStatus.Idle &&
            view.task.queue.isEmpty() &&
            view.pending.isEmpty() &&
            (view.task.place == TaskPlace.Active || view.task.place == TaskPlace.Pinned) &&
            (view.stats.contextUsed ?: 0) >= minTokens

    companion object {
        /** Early enough that a slow summary still starts inside the cache's lifetime, as T3 Code does. */
        const val LEAD_MS = 2 * 60_000L
    }
}
