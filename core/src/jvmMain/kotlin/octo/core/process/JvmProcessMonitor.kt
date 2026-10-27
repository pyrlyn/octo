package octo.core.process

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.jvm.optionals.getOrNull

/**
 * The JDK's [ProcessHandle] walks the tree and reads CPU time; it has no memory figure, so
 * resident memory comes from `ps` (macOS, Linux) or `Get-Process` (Windows), one call per sample.
 * On macOS the JDK reports CPU time for its own process only, so `ps` supplies that too.
 */
class JvmProcessMonitor(
    private val launcher: ProcessLauncher,
    private val windows: Boolean = System.getProperty("os.name").lowercase().contains("windows"),
    private val clock: () -> Long = System::nanoTime,
) : ProcessMonitor {
    private val lock = Mutex()
    private val last = mutableMapOf<Long, CpuSample>()

    override suspend fun sample(pids: Set<Long>): Map<Long, ProcessUsage> {
        val trees = withContext(Dispatchers.IO) { pids.mapNotNull { pid -> tree(pid)?.let { pid to it } }.toMap() }
        val now = clock()
        val figures =
            listed(
                trees.values
                    .flatten()
                    .map { it.pid() }
                    .toSet(),
            )
        return lock.withLock {
            last.keys.retainAll(trees.keys)
            trees.mapValues { (root, tree) ->
                val cpu = cpuNanos(tree, figures)?.let { CpuSample(it, now) }
                val previous = last[root]
                cpu?.let { last[root] = it }
                ProcessUsage(
                    cpuPercent = if (cpu != null && previous != null) cpuPercent(previous, cpu) else null,
                    memoryBytes = tree.mapNotNull { figures[it.pid()]?.memoryBytes }.takeIf { it.isNotEmpty() }?.sum(),
                )
            }
        }
    }

    private fun tree(pid: Long): List<ProcessHandle>? {
        val root = ProcessHandle.of(pid).getOrNull()?.takeIf { it.isAlive } ?: return null
        return listOf(root) + root.descendants().toList()
    }

    // A child that exited since the last sample takes its CPU time with it; the delta is clamped.
    private fun cpuNanos(
        tree: List<ProcessHandle>,
        figures: Map<Long, Listed>,
    ): Long? =
        tree
            .mapNotNull { process ->
                process
                    .info()
                    .totalCpuDuration()
                    .getOrNull()
                    ?.toNanos() ?: figures[process.pid()]?.cpuNanos
            }.takeIf { it.isNotEmpty() }
            ?.sum()

    private suspend fun listed(pids: Set<Long>): Map<Long, Listed> {
        if (pids.isEmpty()) return emptyMap()
        val list = pids.joinToString(",")
        val spec =
            if (windows) {
                val script =
                    "Get-Process -Id $list -ErrorAction SilentlyContinue | " +
                        "ForEach-Object { \"\$(\$_.Id) \$(\$_.WorkingSet64)\" }"
                ProcessSpec("powershell", listOf("-NoProfile", "-NonInteractive", "-Command", script))
            } else {
                ProcessSpec("ps", listOf("-o", "pid=,rss=,time=", "-p", list))
            }
        // ps exits non-zero when one of the pids is gone but still prints the others.
        return try {
            parseListing(launcher.run(spec).stdout, if (windows) 1 else KIB)
        } catch (_: IOException) {
            emptyMap()
        }
    }

    internal data class CpuSample(
        val cpuNanos: Long,
        val wallNanos: Long,
    )

    internal data class Listed(
        val memoryBytes: Long,
        val cpuNanos: Long? = null,
    )

    internal companion object {
        private const val KIB = 1024L
        private const val PERCENT = 100.0
        private const val LISTED_COLUMNS = 3
        private const val SEXAGESIMAL = 60
        private const val SECONDS_PER_DAY = 86_400
        private const val NANOS_PER_SECOND = 1_000_000_000.0

        /**
         * `<pid> <amount> [<cpu time>]` lines, the amount in units of [unit] bytes; anything else
         * is skipped.
         */
        fun parseListing(
            output: String,
            unit: Long,
        ): Map<Long, Listed> =
            output
                .lineSequence()
                .mapNotNull { line ->
                    val parts = line.trim().split(Regex("\\s+"))
                    val pid = parts.getOrNull(0)?.toLongOrNull()
                    val amount = parts.getOrNull(1)?.toLongOrNull()
                    if (parts.size in 2..LISTED_COLUMNS && pid != null && amount != null) {
                        pid to Listed(amount * unit, parts.getOrNull(2)?.let(::cpuTime))
                    } else {
                        null
                    }
                }.toMap()

        /** `ps` CPU time: `[[dd-]hh:]mm:ss[.cc]` in nanoseconds, or null when it does not parse. */
        fun cpuTime(text: String): Long? {
            val days = text.substringBefore('-', "0").toIntOrNull()
            val fields = text.substringAfter('-').split(':')
            val clock = fields.mapNotNull { it.toDoubleOrNull() }.filter { it >= 0 }
            if (days == null || clock.size != fields.size) return null
            val seconds = clock.fold(0.0) { total, part -> total * SEXAGESIMAL + part }
            return ((days * SECONDS_PER_DAY + seconds) * NANOS_PER_SECOND).toLong()
        }

        fun cpuPercent(
            previous: CpuSample,
            next: CpuSample,
        ): Double? {
            val wall = next.wallNanos - previous.wallNanos
            if (wall <= 0) return null
            return (next.cpuNanos - previous.cpuNanos).coerceAtLeast(0) * PERCENT / wall
        }
    }
}
