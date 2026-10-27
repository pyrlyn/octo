package octo.core.process

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
