package octo.core.usage

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
    /** Since [sinceMs] (epoch milliseconds), or everything with null. */
    suspend fun totals(sinceMs: Long?): UsageSummary {
        val warnings = mutableListOf<String>()
        val fromAgents =
            histories.flatMap { (agent, history) ->
                val records =
                    try {
                        history.read()
                    } catch (e: IOException) {
                        // Fail open: one unreadable history leaves the others and Octo's log.
                        warnings += "Could not read ${names[agent] ?: agent.value}'s history: ${e.message}"
                        emptyList()
                    }
                summarize(records.filter { it.agent == agent }, UsageSource.Agent, sinceMs)
            }
        val fromOcto = summarize(log.records.value.filter { it.agent !in histories }, UsageSource.Octo, sinceMs)
        return UsageSummary((fromAgents + fromOcto).sortedByDescending { it.tokens }, warnings)
    }
}
