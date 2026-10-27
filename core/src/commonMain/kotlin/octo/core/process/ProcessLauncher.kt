package octo.core.process

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList

data class ProcessSpec(
    val command: String,
    val args: List<String> = emptyList(),
    val workingDir: String? = null,
    /** Added to (or overriding) the launcher's base environment. */
    val env: Map<String, String> = emptyMap(),
)

/** A spawned process with line-framed stdio. */
interface RunningProcess {
    /** The OS process id on this machine; null where there is none, as with a fake process. */
    val pid: Long? get() = null

    /** stdout lines; single collector; completes at EOF. */
    val stdout: Flow<String>

    /** stderr lines; single collector; completes at EOF. */
    val stderr: Flow<String>

    suspend fun writeLine(line: String)

    suspend fun closeStdin()

    suspend fun awaitExit(): Int

    /** Ends the process and every process it started. */
    fun kill()
}

/** Platform process spawning, kept behind an interface so the core compiles for every target. */
interface ProcessLauncher {
    /** Absolute path of [command] found on PATH, or null. */
    suspend fun which(command: String): String?

    suspend fun launch(spec: ProcessSpec): RunningProcess
}

data class ProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

/** Runs a short command to completion, collecting its output; cancelling it kills the command. */
suspend fun ProcessLauncher.run(
    spec: ProcessSpec,
    stdin: String? = null,
): ProcessResult =
    coroutineScope {
        val process = launch(spec)
        try {
            val out = async { process.stdout.toList() }
            val err = async { process.stderr.toList() }
            stdin?.lineSequence()?.forEach { process.writeLine(it) }
            process.closeStdin()
            val code = process.awaitExit()
            ProcessResult(code, out.await().joinToString("\n"), err.await().joinToString("\n"))
        } catch (e: CancellationException) {
            process.kill()
            throw e
        }
    }
