package octo.adapters.base

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import octo.core.agent.AgentEvent
import octo.core.agent.AgentSession
import octo.core.agent.LogStream
import octo.core.agent.SessionSpec
import octo.core.process.RunningProcess
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * An agent session backed by one process speaking line-framed text: stderr becomes log events,
 * stdout goes to [readOutput], and the session ends with [AgentEvent.Exited] once the process does.
 */
abstract class ProcessAgentSession(
    protected val process: RunningProcess,
    context: CoroutineContext,
) : AgentSession {
    protected val scope = CoroutineScope(SupervisorJob() + context)
    private val channel = Channel<AgentEvent>(Channel.UNLIMITED)

    override val events: Flow<AgentEvent> = channel.receiveAsFlow()

    protected fun emit(event: AgentEvent) {
        channel.trySend(event)
    }

    /** Consumes stdout until it ends. */
    protected abstract suspend fun readOutput(lines: Flow<String>)

    /** Runs once output is being read, before the session is handed out (handshake, `session/new`). */
    open suspend fun initialize(spec: SessionSpec) {}

    // A parser bug surfaces as a failed agent, not a crashed Octo.
    @Suppress("TooGenericExceptionCaught")
    internal fun start() {
        scope.launch {
            coroutineScope {
                launch { process.stderr.collect { emit(AgentEvent.Log(LogStream.Stderr, it)) } }
                try {
                    readOutput(process.stdout)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emit(AgentEvent.Failed("Could not read agent output: ${e.message}"))
                    process.kill()
                }
            }
            emit(AgentEvent.Exited(process.awaitExit()))
            channel.close()
            scope.cancel()
        }
    }

    override suspend fun close() {
        process.closeStdin()
        process.kill()
    }
}
