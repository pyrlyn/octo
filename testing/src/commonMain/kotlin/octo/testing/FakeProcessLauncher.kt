package octo.testing

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import octo.core.process.ProcessLauncher
import octo.core.process.ProcessSpec
import octo.core.process.RunningProcess

/**
 * Stands in for real agent binaries: each launch runs [script] as the "process", which reads what
 * Octo writes and answers with recorded output. No real CLI or account is ever touched.
 */
class FakeProcessLauncher(
    private val scope: CoroutineScope,
    private val binaries: Map<String, String> = emptyMap(),
    private val script: suspend FakeProcess.() -> Unit,
) : ProcessLauncher {
    private val launchedSpecs = mutableListOf<ProcessSpec>()
    private val launchedProcesses = mutableListOf<FakeProcess>()

    val launched: List<ProcessSpec> get() = launchedSpecs.toList()
    val processes: List<FakeProcess> get() = launchedProcesses.toList()

    override suspend fun which(command: String): String? = binaries[command]

    override suspend fun launch(spec: ProcessSpec): RunningProcess {
        val process = FakeProcess(spec)
        launchedSpecs += spec
        launchedProcesses += process
        process.job =
            scope.launch {
                try {
                    process.script()
                    process.exit(0)
                } finally {
                    process.exit(FakeProcess.KILLED)
                }
            }
        return process
    }
}

class FakeProcess(
    val spec: ProcessSpec,
) : RunningProcess {
    private val out = Channel<String>(Channel.UNLIMITED)
    private val err = Channel<String>(Channel.UNLIMITED)
    private val input = Channel<String>(Channel.UNLIMITED)
    private val exitCode = CompletableDeferred<Int>()
    private val writtenLines = mutableListOf<String>()
    internal var job: Job? = null

    /** Every line Octo wrote to stdin, in order. */
    val written: List<String> get() = writtenLines.toList()
    var killed: Boolean = false
        private set
    var stdinClosed: Boolean = false
        private set

    override val stdout: Flow<String> = out.receiveAsFlow()
    override val stderr: Flow<String> = err.receiveAsFlow()

    override suspend fun writeLine(line: String) {
        check(!stdinClosed) { "stdin is closed" }
        writtenLines += line
        input.send(line)
    }

    override suspend fun closeStdin() {
        stdinClosed = true
        input.close()
    }

    override suspend fun awaitExit(): Int = exitCode.await()

    override fun kill() {
        killed = true
        job?.cancel()
        exit(KILLED)
    }

    /** Next line Octo wrote, or null once stdin is closed. Called from the script. */
    suspend fun readLine(): String? = input.receiveCatching().getOrNull()

    fun emit(line: String) {
        out.trySend(line)
    }

    fun emitError(line: String) {
        err.trySend(line)
    }

    fun exit(code: Int) {
        if (exitCode.isCompleted) return
        out.close()
        err.close()
        exitCode.complete(code)
    }

    companion object {
        const val KILLED = 137
    }
}
