package octo.feature.agents

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import octo.core.agent.AgentIcon
import octo.core.agent.AuthState
import octo.core.agent.SessionStats
import octo.core.orchestrator.AgentCatalog
import octo.core.orchestrator.AgentInfo
import octo.core.orchestrator.OctoCore
import octo.core.process.ProcessUsage
import octo.core.status.AgentStatus
import octo.core.task.OctoState
import octo.core.task.TaskId
import octo.core.task.TaskView
import octo.feature.common.NONE
import octo.feature.common.action
import octo.feature.common.childScope
import octo.feature.common.tokenCount
import octo.feature.common.userMessage
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

data class AgentRow(
    val id: TaskId,
    val title: String,
    val agent: String,
    val icon: AgentIcon?,
    val credentials: String,
    val auth: AuthState?,
    val cpu: String,
    val memory: String,
    val model: String,
    val context: String,
    /** How full the context window is, 0 to 1, when the agent reports both figures. */
    val contextShare: Float?,
    val tokens: String,
    val stopping: Boolean,
)

data class AgentsUiState(
    val rows: List<AgentRow> = emptyList(),
    val error: String? = null,
)

/** The running agents, each with its own process figures and what it reports about its session. */
class AgentsViewModel(
    private val core: OctoCore,
    private val catalog: AgentCatalog,
    parent: CoroutineScope,
) {
    private val scope = parent.childScope()

    /** Probed agents per machine (null for this computer); a machine is probed once, when first needed. */
    private val agents = MutableStateFlow<Map<String?, List<AgentInfo>>>(emptyMap())
    private val probing = mutableSetOf<String?>()
    private val stopping = MutableStateFlow<Set<TaskId>>(emptySet())
    private val error = MutableStateFlow<String?>(null)

    val state: StateFlow<AgentsUiState> =
        combine(
            core.state,
            core.processUsage.onStart { emit(emptyMap()) },
            agents,
            stopping,
            error,
        ) { octo, usage, probed, stops, message ->
            AgentsUiState(running(octo).map { row(it, usage[it.task.id], probed, it.task.id in stops) }, message)
        }.stateIn(scope, SharingStarted.Eagerly, AgentsUiState())

    init {
        scope.launch {
            core.state.collect { octo -> running(octo).map { it.task.machine }.distinct().forEach(::probe) }
        }
    }

    fun stop(id: TaskId) {
        stopping.update { it + id }
        scope.action({ e -> error.value = e.userMessage }) {
            try {
                core.stop(id)
            } finally {
                stopping.update { it - id }
            }
        }
    }

    fun dismissError() {
        error.value = null
    }

    fun close() = scope.cancel()

    // The sign-in state is only decoration here, so a machine that cannot be probed shows "Unknown".
    @Suppress("TooGenericExceptionCaught")
    private fun probe(machine: String?) {
        if (!probing.add(machine)) return
        scope.launch {
            val found =
                try {
                    catalog.agents(machine)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    emptyList()
                }
            agents.update { it + (machine to found) }
        }
    }

    private fun row(
        view: TaskView,
        usage: ProcessUsage?,
        probed: Map<String?, List<AgentInfo>>,
        stopping: Boolean,
    ): AgentRow {
        val task = view.task
        val info = probed[task.machine]?.firstOrNull { it.id == task.agent }
        val remote = task.machine != null
        return AgentRow(
            id = task.id,
            title = task.title,
            agent = info?.displayName ?: task.agent.value,
            icon = info?.icon,
            credentials = credentialLabel(info?.probe?.auth),
            auth = info?.probe?.auth,
            cpu = if (remote) REMOTE else cpuLabel(usage?.cpuPercent),
            memory = if (remote) REMOTE else memoryLabel(usage?.memoryBytes),
            model = view.stats.model ?: NONE,
            context = contextLabel(view.stats),
            contextShare = contextShare(view.stats),
            tokens = tokensLabel(view.stats),
            stopping = stopping,
        )
    }

    private fun running(state: OctoState) = state.tasks.filter { it.status in RUNNING }

    private companion object {
        val RUNNING = setOf(AgentStatus.Starting, AgentStatus.Working, AgentStatus.Blocked, AgentStatus.Idle)
    }
}

/** Shown in place of a credential: Octo never reads one, so there is nothing to unmask. */
const val CREDENTIAL_MASK = "••••••"
const val REMOTE = "remote"

/** Only the sign-in state is ever shown, never a credential, which Octo does not read. */
fun credentialLabel(auth: AuthState?): String =
    when (auth) {
        AuthState.LoggedIn -> CREDENTIAL_MASK
        AuthState.LoggedOut -> "Not signed in"
        AuthState.Unknown, null -> "Unknown"
    }

internal fun cpuLabel(percent: Double?): String = percent?.let { String.format(Locale.ROOT, "%.1f%%", it) } ?: NONE

private const val KIB = 1024.0
private const val PERCENT = 100

fun memoryLabel(bytes: Long?): String {
    if (bytes == null) return NONE
    val mib = bytes / KIB / KIB
    return if (mib < KIB) {
        String.format(Locale.ROOT, "%.0f MB", mib)
    } else {
        String.format(Locale.ROOT, "%.1f GB", mib / KIB)
    }
}

fun contextLabel(stats: SessionStats): String {
    val used = stats.contextUsed
    val window = stats.contextWindow
    return when {
        used == null -> window?.let { "? / ${tokenCount(it)}" } ?: NONE
        window == null || window <= 0 -> tokenCount(used)
        else -> "${tokenCount(used)} / ${tokenCount(window)} (${used * PERCENT / window}%)"
    }
}

fun contextShare(stats: SessionStats): Float? {
    val used = stats.contextUsed
    val window = stats.contextWindow
    return if (used == null || window == null || window <= 0) null else (used.toFloat() / window).coerceIn(0f, 1f)
}

fun tokensLabel(stats: SessionStats): String {
    val parts =
        listOfNotNull(
            stats.inputTokens?.let { "in ${tokenCount(it)}" },
            stats.outputTokens?.let { "out ${tokenCount(it)}" },
            stats.cacheReadTokens?.let { "cache read ${tokenCount(it)}" },
            stats.cacheWriteTokens?.let { "cache write ${tokenCount(it)}" },
        )
    return parts.joinToString(" · ").ifEmpty { NONE }
}
