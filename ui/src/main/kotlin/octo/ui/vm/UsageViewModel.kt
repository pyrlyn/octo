package octo.ui.vm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import octo.core.agent.AgentIcon
import octo.core.orchestrator.AgentCatalog
import octo.core.orchestrator.AgentInfo
import octo.core.task.UsageLimit
import octo.core.usage.UsageReport
import octo.core.usage.UsageSource
import octo.core.usage.UsageTotal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

enum class UsagePeriod(
    val label: String,
) {
    Today("Today"),
    Week("7 days"),
    Month("30 days"),
    All("All time"),
}

data class UsageRow(
    val agent: String,
    val icon: AgentIcon?,
    val model: String,
    /** Where the figures were counted. */
    val source: String,
    val input: String,
    val output: String,
    val cacheRead: String,
    val cacheWrite: String,
    val total: String,
)

data class UsageUiState(
    val period: UsagePeriod = UsagePeriod.Week,
    val rows: List<UsageRow> = emptyList(),
    val total: String = "0",
    val loading: Boolean = true,
    /** Agent histories that could not be read; the rest is still counted. */
    val warnings: List<String> = emptyList(),
    val error: String? = null,
)

/** Token use per agent and model over a chosen period. */
class UsageViewModel(
    private val report: UsageReport,
    private val catalog: AgentCatalog,
    parent: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val scope = parent.childScope()
    private val mutable = MutableStateFlow(UsageUiState())
    val state: StateFlow<UsageUiState> = mutable.asStateFlow()
    private var agents: List<AgentInfo>? = null
    private var loading: Job? = null

    init {
        load()
    }

    fun choose(period: UsagePeriod) {
        mutable.update { it.copy(period = period) }
        load()
    }

    fun refresh() = load()

    fun dismissError() = mutable.update { it.copy(error = null) }

    fun close() = scope.cancel()

    private fun load() {
        loading?.cancel()
        val period = state.value.period
        mutable.update { it.copy(loading = true) }
        loading =
            scope.action({ e -> mutable.update { it.copy(loading = false, error = e.userMessage) } }) {
                val summary = report.totals(since(period, now(), zone))
                val known = agents ?: probe().also { agents = it }
                mutable.update { state ->
                    state.copy(
                        rows = summary.totals.map { row(it, known) },
                        total = tokenCount(summary.totals.sumOf { it.tokens }),
                        loading = false,
                        warnings = summary.warnings,
                    )
                }
            }
    }

    // Names and icons are only decoration, so an agent that cannot be probed shows its id.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun probe(): List<AgentInfo> =
        try {
            catalog.agents()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }

    private fun row(
        total: UsageTotal,
        agents: List<AgentInfo>,
    ): UsageRow {
        val info = agents.firstOrNull { it.id == total.agent }
        return UsageRow(
            agent = info?.displayName ?: total.agent.value,
            icon = info?.icon,
            model = total.model ?: NONE,
            source =
                when (total.source) {
                    UsageSource.Agent -> "Agent history"
                    UsageSource.Octo -> "Octo"
                },
            input = tokenCount(total.inputTokens),
            output = tokenCount(total.outputTokens),
            cacheRead = tokenCount(total.cacheReadTokens),
            cacheWrite = tokenCount(total.cacheWriteTokens),
            total = tokenCount(total.tokens),
        )
    }
}

private const val DAYS_IN_WEEK = 7L
private const val DAYS_IN_MONTH = 30L

/** Where [period] starts, in epoch milliseconds; null for all time. Days start at local midnight. */
internal fun since(
    period: UsagePeriod,
    nowMs: Long,
    zone: ZoneId,
): Long? {
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()

    fun start(day: LocalDate) = day.atStartOfDay(zone).toInstant().toEpochMilli()
    return when (period) {
        UsagePeriod.Today -> start(today)
        UsagePeriod.Week -> start(today.minusDays(DAYS_IN_WEEK - 1))
        UsagePeriod.Month -> start(today.minusDays(DAYS_IN_MONTH - 1))
        UsagePeriod.All -> null
    }
}

/** "Usage limit reached" with when it lifts: the time today, else the date as well. */
internal fun limitLabel(
    limit: UsageLimit,
    nowMs: Long,
    zone: ZoneId,
): String {
    val resets = limit.resetsAtMs?.let { Instant.ofEpochMilli(it).atZone(zone) } ?: return "Usage limit reached"
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val pattern = if (resets.toLocalDate() == today) "HH:mm" else "MMM d, HH:mm"
    return "Limited until ${resets.format(DateTimeFormatter.ofPattern(pattern, Locale.ROOT))}"
}
