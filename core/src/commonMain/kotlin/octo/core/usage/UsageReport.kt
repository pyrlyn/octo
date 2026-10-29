package octo.core.usage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.io.IOException
import octo.core.agent.AgentId

/**
 * Totals per agent and model. An agent that keeps its own history is counted from it, which also
 * covers its sessions outside Octo; every other agent is counted from Octo's [log], never both.
 */
class UsageReport(
    private val log: UsageLog,
    private val histories: Map<AgentId, UsageHistory>,
    /** The name a warning gives an agent. */
    private val names: Map<AgentId, String> = emptyMap(),
) {
    /**
     * Emits now and whenever Octo records a finished turn, which is when [totals] may have moved.
     * Every agent's turns are logged, so this also covers the agents counted from their histories.
     */
    val changes: Flow<Unit> get() = log.records.map { }

    /**
     * Since [sinceMs] (epoch milliseconds), or everything with null. With [edges], the ascending
     * starts of time buckets, [UsageSummary.series] also holds the tokens counted in each bucket;
     * the last bucket runs to now. The caller picks the edges, so days follow its own time zone.
     */
    suspend fun totals(
        sinceMs: Long?,
        edges: List<Long> = emptyList(),
    ): UsageSummary {
        val warnings = mutableListOf<String>()
        val fromAgents =
            histories.flatMap { (agent, history) ->
                try {
                    history.read().filter { it.agent == agent }
                } catch (e: IOException) {
                    // Fail open: one unreadable history leaves the others and Octo's log.
                    warnings += "Could not read ${names[agent] ?: agent.value}'s history: ${e.message}"
                    emptyList()
                }
            }
        val fromOcto = log.records.value.filter { it.agent !in histories }
        val totals = summarize(fromAgents, UsageSource.Agent, sinceMs) + summarize(fromOcto, UsageSource.Octo, sinceMs)
        return UsageSummary(totals.sortedByDescending { it.tokens }, warnings, series(fromAgents + fromOcto, edges))
    }
}
