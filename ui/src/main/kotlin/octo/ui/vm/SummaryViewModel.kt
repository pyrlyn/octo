package octo.ui.vm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import octo.core.usage.UsageReport
import octo.feature.agents.AgentsUiState
import octo.feature.common.childScope
import octo.feature.common.label
import octo.feature.common.tokenCount
import octo.feature.usage.UsagePeriod
import octo.feature.usage.since
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException

data class SummaryUiState(
    /** Tokens used since local midnight; null while the `usage` flag is off or before the first count. */
    val todayTokens: Long? = null,
    val running: Int = 0,
) {
    /** "Today 251k tokens · 3 agents", or null when there is nothing to say. */
    val label: String?
        get() =
            listOfNotNull(
                todayTokens?.let { "Today ${tokenCount(it)} tokens" },
                running.takeIf { it > 0 }?.let { if (it == 1) "1 agent" else "$it agents" },
            ).joinToString(" · ").ifEmpty { null }
}

/** The window's one-line summary: today's tokens and how many agents run. */
class SummaryViewModel(
    agents: StateFlow<AgentsUiState>,
    report: UsageReport?,
    parent: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
    refreshMs: Long = REFRESH_MS,
) {
    private val scope = parent.childScope()
    private val today = MutableStateFlow<Long?>(null)

    val state: StateFlow<SummaryUiState> =
        combine(today, agents) { tokens, running -> SummaryUiState(tokens, running.rows.size) }
            .stateIn(scope, SharingStarted.Eagerly, SummaryUiState())

    init {
        // A recorded turn moves the count; the timer catches midnight and sessions run outside Octo.
        report?.let { usage ->
            scope.launch { merge(usage.changes, ticks(refreshMs)).conflate().collect { count(usage) } }
        }
    }

    fun close() = scope.cancel()

    // Only a summary: a failed count keeps the last figure rather than showing an error.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun count(usage: UsageReport) {
        try {
            today.value = usage.totals(since(UsagePeriod.Today, now(), zone)).totals.sumOf { it.tokens }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Keeps the last figure.
        }
    }
}

private fun ticks(periodMs: Long): Flow<Unit> =
    flow {
        while (true) {
            delay(periodMs)
            emit(Unit)
        }
    }

private const val REFRESH_MS = 10 * 60 * 1_000L
