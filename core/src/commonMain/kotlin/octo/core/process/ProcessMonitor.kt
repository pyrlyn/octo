package octo.core.process

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** CPU and resident memory of a process and every process it started; null is unknown. */
data class ProcessUsage(
    /** 100 is one core fully busy, so a busy tree can go above 100. */
    val cpuPercent: Double?,
    val memoryBytes: Long?,
)

/** Platform process sampling, kept behind an interface so the core compiles for every target. */
interface ProcessMonitor {
    /**
     * Samples each root in [pids] with its descendants. CPU needs two samples of the same root,
     * so the first one reports it as unknown. A root that is gone is left out of the result.
     */
    suspend fun sample(pids: Set<Long>): Map<Long, ProcessUsage>
}

/** Samples the roots [roots] names every [periodMs] for as long as the flow is collected. */
fun <K> ProcessMonitor.usage(
    periodMs: Long,
    roots: () -> Map<K, Long>,
): Flow<Map<K, ProcessUsage>> =
    flow {
        while (true) {
            val current = roots()
            val sampled = sample(current.values.toSet())
            emit(current.mapNotNull { (key, pid) -> sampled[pid]?.let { key to it } }.toMap())
            delay(periodMs)
        }
    }
