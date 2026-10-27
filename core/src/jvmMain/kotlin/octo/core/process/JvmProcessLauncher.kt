package octo.core.process

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream

class JvmProcessLauncher(
    /** The environment children start from; tests pass a sealed-off map instead of the real one. */
    private val baseEnv: Map<String, String> = System.getenv(),
) : ProcessLauncher {
    private val windows = System.getProperty("os.name").lowercase().contains("windows")

    override suspend fun which(command: String): String? =
        withContext(Dispatchers.IO) {
            val candidates =
                if (command.contains(File.separatorChar) || command.contains('/')) {
                    listOf(File(command))
                } else {
                    val extensions =
                        if (windows) {
                            listOf("") + (baseEnv["PATHEXT"] ?: ".EXE;.CMD;.BAT").split(';')
                        } else {
                            listOf("")
                        }
                    (baseEnv["PATH"] ?: "")
                        .split(File.pathSeparatorChar)
                        .filter { it.isNotBlank() }
                        .flatMap { dir -> extensions.map { ext -> File(dir, command + ext) } }
                }
            candidates.firstOrNull { it.isFile && it.canExecute() }?.absolutePath
        }

    override suspend fun launch(spec: ProcessSpec): RunningProcess =
        withContext(Dispatchers.IO) {
            val builder = ProcessBuilder(listOf(spec.command) + spec.args)
            spec.workingDir?.let { builder.directory(File(it)) }
            builder.environment().apply {
                clear()
                putAll(baseEnv)
                putAll(spec.env)
            }
            JvmRunningProcess(builder.start())
        }
}

private class JvmRunningProcess(
    private val process: Process,
) : RunningProcess {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val out = pump(process.inputStream)
    private val err = pump(process.errorStream)
    private val writer = process.outputStream.bufferedWriter()
    private val writeLock = Mutex()

    override val pid: Long = process.pid()

    override val stdout: Flow<String> = out.receiveAsFlow()
    override val stderr: Flow<String> = err.receiveAsFlow()

    // Reading starts at spawn so nothing is lost before a collector subscribes.
    private fun pump(stream: InputStream): Channel<String> {
        val channel = Channel<String>(Channel.UNLIMITED)
        scope.launch {
            try {
                stream.bufferedReader().useLines { lines -> lines.forEach { channel.trySend(it) } }
            } catch (_: IOException) {
                // The stream closes when the process is killed; EOF is the only outcome that matters.
            } finally {
                channel.close()
            }
        }
        return channel
    }

    override suspend fun writeLine(line: String) {
        writeLock.withLock {
            withContext(Dispatchers.IO) {
                writer.write(line)
                writer.newLine()
                writer.flush()
            }
        }
    }

    override suspend fun closeStdin() {
        writeLock.withLock {
            withContext(Dispatchers.IO) {
                try {
                    writer.close()
                } catch (_: IOException) {
                    // Already closed because the process exited.
                }
            }
        }
    }

    override suspend fun awaitExit(): Int {
        val code = process.onExit().await().exitValue()
        scope.launch {
            delay(STREAM_DRAIN_MS)
            scope.cancel()
        }
        return code
    }

    override fun kill() {
        process.descendants().forEach { it.destroy() }
        process.destroy()
        scope.launch {
            delay(KILL_GRACE_MS)
            process.descendants().forEach { it.destroyForcibly() }
            process.destroyForcibly()
        }
    }

    private companion object {
        const val KILL_GRACE_MS = 3_000L
        const val STREAM_DRAIN_MS = 5_000L
    }
}
