package octo.adapters.zcode

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import octo.adapters.base.plusTokens
import octo.core.agent.AgentEvent
import octo.core.agent.AgentSession
import octo.core.agent.AgentSessionRef
import octo.core.agent.LogStream
import octo.core.agent.PermissionMode
import octo.core.agent.PromptImage
import octo.core.agent.RequestResponse
import octo.core.agent.SessionSpec
import octo.core.agent.SessionStats
import octo.core.process.ProcessLauncher
import octo.core.process.ProcessSpec
import octo.core.process.RunningProcess
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * A ZCode session as a run of `zcode --prompt` processes, one per turn: the first starts the
 * session and every later one resumes it by the id the runs report. The session itself has no
 * process, so it ends when Octo closes it.
 */
internal class ZCodeSession(
    private val launcher: ProcessLauncher,
    private val binary: String,
    private val spec: SessionSpec,
    context: CoroutineContext,
) : AgentSession {
    private val scope = CoroutineScope(SupervisorJob() + context)
    private val channel = Channel<AgentEvent>(Channel.UNLIMITED)
    private val lock = Mutex()
    private var sessionId = spec.resume?.value
    private var mode = spec.permissionMode
    private var running: RunningProcess? = null
    private var turn: Job? = null
    private var cancelled = false
    private var totals = SessionStats()

    @Volatile
    private var pid: Long? = null

    override val events: Flow<AgentEvent> = channel.receiveAsFlow()

    override val processId: Long? get() = pid

    init {
        spec.resume?.let { emit(AgentEvent.SessionReady(it)) }
    }

    private fun emit(event: AgentEvent) {
        channel.trySend(event)
    }

    override suspend fun prompt(
        text: String,
        images: List<PromptImage>,
    ) {
        lock.withLock {
            check(turn == null) { "ZCode is still running the previous turn" }
            val process = launcher.launch(ProcessSpec(binary, args(text, images), spec.workingDir, spec.env))
            running = process
            pid = process.pid
            cancelled = false
            emit(AgentEvent.TurnStarted)
            turn = scope.launch { run(process) }
        }
    }

    private fun args(
        text: String,
        images: List<PromptImage>,
    ) = buildList {
        // `name=value` keeps a prompt that starts with `-` from reading as another option.
        add("--prompt=$text")
        add("--cwd=${spec.workingDir}")
        add("--mode=${modeFlag(mode)}")
        add("--output-format=stream-json")
        // A headless run has no client to answer these, so they could only fail the turn.
        add("--disallowed-tools=AskUserQuestion EnterPlanMode ExitPlanMode")
        add("--no-color")
        sessionId?.let { add("--resume=$it") }
        images.forEach { add("--attach=${it.path}") }
    }

    // A parser bug surfaces as a failed turn, not a crashed Octo.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun run(process: RunningProcess) {
        val reader = ZCodeTurn(::emit, ::ready)
        var lastError: String? = null
        try {
            coroutineScope {
                launch {
                    process.stderr.collect {
                        lastError = it
                        emit(AgentEvent.Log(LogStream.Stderr, it))
                    }
                }
                process.stdout.collect { reader.read(it) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(AgentEvent.Failed("Could not read ZCode output: ${e.message}"))
            process.kill()
        }
        val code = process.awaitExit()
        val (stats, end) =
            lock.withLock {
                running = null
                pid = null
                turn = null
                val stats = reader.stats?.let { turnStats -> totals.plusTokens(turnStats).also { totals = it } }
                stats to reader.end(code, cancelled, lastError)
            }
        // Context figures are the latest run's own, not a sum.
        stats?.let { emit(AgentEvent.StatsUpdated(it.merge(reader.stats.contextOnly()))) }
        emit(end)
    }

    private suspend fun ready(id: String) {
        val changed = lock.withLock { (sessionId != id).also { sessionId = id } }
        if (changed) emit(AgentEvent.SessionReady(AgentSessionRef(id)))
    }

    // Headless runs never ask: tools that would need an answer fail inside the run instead.
    override suspend fun respond(
        requestId: String,
        response: RequestResponse,
    ) = Unit

    override suspend fun setPermissionMode(mode: PermissionMode) {
        lock.withLock { this.mode = mode }
    }

    override suspend fun cancel() {
        lock.withLock {
            running?.let {
                cancelled = true
                it.kill()
            }
        }
    }

    override suspend fun close() {
        cancel()
        lock.withLock { turn }?.join()
        emit(AgentEvent.Exited(0))
        channel.close()
        scope.cancel()
    }

    private companion object {
        fun modeFlag(mode: PermissionMode) =
            when (mode) {
                PermissionMode.Supervised -> "build"
                PermissionMode.AutoAcceptEdits -> "edit"
                PermissionMode.Auto -> "guarded"
                PermissionMode.FullAccess -> "yolo"
            }

        fun SessionStats?.contextOnly() =
            SessionStats(contextUsed = this?.contextUsed, contextWindow = this?.contextWindow)
    }
}
