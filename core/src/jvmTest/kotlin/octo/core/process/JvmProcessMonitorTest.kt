package octo.core.process

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import octo.core.process.JvmProcessMonitor.Companion.cpuPercent
import octo.core.process.JvmProcessMonitor.Companion.cpuTime
import octo.core.process.JvmProcessMonitor.Companion.parseListing
import octo.core.process.JvmProcessMonitor.CpuSample
import octo.core.process.JvmProcessMonitor.Listed
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JvmProcessMonitorTest {
    private val windows = System.getProperty("os.name").lowercase().contains("windows")
    private val launcher = JvmProcessLauncher(mapOf("PATH" to (System.getenv("PATH") ?: "")))

    @Test
    fun psAndGetProcessOutputParse() {
        assertEquals(
            mapOf(12L to Listed(2048L, 1_500_000_000L), 7L to Listed(1024L, 61_000_000_000L)),
            parseListing("   12  2   0:01.50\n7 1 01:01\n", unit = 1024),
        )
        assertEquals(mapOf(5L to Listed(300L)), parseListing("5 300\nnot a line\n\n6\n", unit = 1))
    }

    @Test
    fun psCpuTimeParses() {
        assertEquals(2_500_000_000L, cpuTime("0:02.50"))
        assertEquals(3_723_000_000_000L, cpuTime("01:02:03"))
        assertEquals(90_000_000_000_000L, cpuTime("1-01:00:00"))
        assertNull(cpuTime("soon"))
        assertNull(cpuTime("1:-2"))
    }

    @Test
    fun cpuIsTheShareOfWallTimeAndNeverNegative() {
        assertEquals(50.0, cpuPercent(CpuSample(0, 0), CpuSample(500, 1_000)))
        assertEquals(200.0, cpuPercent(CpuSample(0, 0), CpuSample(2_000, 1_000)))
        // A child that exited took its CPU time with it.
        assertEquals(0.0, cpuPercent(CpuSample(900, 0), CpuSample(100, 1_000)))
        assertNull(cpuPercent(CpuSample(0, 5), CpuSample(10, 5)))
    }

    @Test
    fun samplesAChildProcessTree() =
        runBlocking {
            if (windows) return@runBlocking
            val child = ProcessBuilder("sh", "-c", "sleep 30 & wait").start()
            try {
                val monitor = JvmProcessMonitor(launcher)
                val first = monitor.sample(setOf(child.pid())).getValue(child.pid())
                assertNull(first.cpuPercent, "CPU needs a second sample")
                assertTrue((first.memoryBytes ?: 0) > 0, "resident memory of $first")

                // A freshly started process may not report CPU time yet, so give it a few samples.
                val later =
                    (1..SAMPLES).firstNotNullOfOrNull {
                        delay(SAMPLE_GAP_MS)
                        monitor.sample(setOf(child.pid()))[child.pid()]?.cpuPercent
                    }
                assertNotNull(later)
            } finally {
                child.descendants().forEach { it.destroyForcibly() }
                child.destroyForcibly()
            }
        }

    @Test
    fun aGoneProcessIsLeftOut() =
        runBlocking {
            if (windows) return@runBlocking
            val child = ProcessBuilder("true").start()
            child.waitFor()
            assertEquals(emptyMap(), JvmProcessMonitor(launcher).sample(setOf(child.pid())))
        }

    @Test
    fun aMissingPsLeavesMemoryUnknown() =
        runBlocking {
            if (windows) return@runBlocking
            val noPs =
                object : ProcessLauncher {
                    override suspend fun which(command: String): String? = null

                    override suspend fun launch(spec: ProcessSpec): RunningProcess =
                        throw IOException("no ${spec.command}")
                }
            val monitor = JvmProcessMonitor(noPs)
            val child = ProcessBuilder("sleep", "30").start()
            try {
                val usage = monitor.sample(setOf(child.pid())).getValue(child.pid())
                assertNull(usage.memoryBytes)
            } finally {
                child.destroyForcibly()
            }
        }

    private companion object {
        const val SAMPLES = 20
        const val SAMPLE_GAP_MS = 50L
    }
}
