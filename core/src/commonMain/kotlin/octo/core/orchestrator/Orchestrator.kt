package octo.core.orchestrator

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.files.Path
import octo.core.agent.AdapterRegistry
import octo.core.agent.AgentEvent
import octo.core.agent.AgentId
import octo.core.agent.AgentSession
import octo.core.agent.AgentSessionRef
import octo.core.agent.Capability
import octo.core.agent.MODEL_OPTION
import octo.core.agent.PermissionMode
import octo.core.agent.RequestResponse
import octo.core.agent.Role
import octo.core.agent.SessionSpec
import octo.core.agent.SessionStats
import octo.core.agent.StopReason
import octo.core.git.TaskDiff
import octo.core.git.Workspace
import octo.core.handoff.Handoff
import octo.core.messaging.McpProvider
import octo.core.persist.StateStore
import octo.core.process.ProcessMonitor
import octo.core.process.ProcessUsage
import octo.core.process.usage
import octo.core.status.AgentState
import octo.core.status.AgentStatus
import octo.core.task.Layout
import octo.core.task.NewTask
import octo.core.task.OctoState
import octo.core.task.Task
import octo.core.task.TaskId
import octo.core.task.TaskPlace
import octo.core.task.TaskView
import octo.core.task.Transcript
import octo.core.task.TranscriptEntry
import octo.core.terms.TermsGate
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

/**
 * Runs tasks: one worktree, one agent session and one supervisor job per task, so a crash in
 * one agent never reaches another. Lifecycle calls for the same task are serialized; different
 * tasks never wait on each other.
 */
@Suppress(
    // It implements the whole of OctoCore, so it carries that interface's size.
    "TooManyFunctions",
)
class Orchestrator private constructor(
    private val machines: Machines,
    store: StateStore,
    private val terms: TermsGate,
    private val scope: CoroutineScope,
    private val newId: () -> String,
    private val mcp: McpProvider?,
    private val processes: ProcessMonitor?,
    private val defaultPermissionMode: PermissionMode,
    worktreeSetup: Map<String, String>,
    private val clock: Clock,
    recordCheckpoints: Boolean,
    agentLimits: Map<String?, Int>,
    relay: String,
    compactIdle: Boolean,
) : OctoCore {
    private val mutableState = MutableStateFlow(OctoState())
    private val transcripts = MutableStateFlow<Map<TaskId, List<TranscriptEntry>>>(emptyMap())
    private val runtimes = MutableStateFlow<Map<TaskId, Runtime>>(emptyMap())
    private val locks = MutableStateFlow<Map<TaskId, Mutex>>(emptyMap())
    private val saver = StateSaver(store, ::warn)
    private val starts =
        StartQueue(agentLimits, scope, mutableState, ::withTask, { running(it) != null }) {
            launchAgent(it.id, it.resume, it.forkFrom)
        }
    private val setup = WorktreeSetup(worktreeSetup, transcripts) { machines[it.machine].workspace }
    private val checkpoints =
        Checkpoints(recordCheckpoints, mutableState, transcripts) { machines[it.machine].workspace }
    private val move = TaskMove(machines, relay)
    private val queues = PromptQueues(mutableState, MAX_QUEUED)
    private val compaction = if (compactIdle) IdleCompaction(clock) else null

    override val state: StateFlow<OctoState> = mutableState.asStateFlow()

    override fun transcript(taskId: TaskId): Flow<List<TranscriptEntry>> =
        transcripts
            .map {
                it[taskId].orEmpty()
            }.distinctUntilChanged()

    override suspend fun createTask(request: NewTask): TaskId {
        val machine = machines[request.machine]
        val adapter = machine.adapter(request.agent)
        terms.requireAcknowledged(adapter)
        val repo = machine.workspace.repoRoot(request.repo)
        val id = uniqueId()
        val slug = slug(request.title, id)
        val worktree = machine.worktree(repo, slug)
        val branch = "$BRANCH_PREFIX$slug"
        machine.workspace.createWorktree(repo, worktree, branch, request.baseRef)
        val task =
            Task(
                id,
                request.title,
                repo,
                worktree,
                branch,
                request.baseRef,
                request.agent,
                request.options,
                machine = request.machine,
                permissionMode = request.permissionMode ?: defaultPermissionMode,
                group = request.group,
            )
        mutableState.update { it.copy(tasks = it.tasks + TaskView(task, AgentStatus.Starting)) }
        withTask(id) {
            if (setUp(id)) startAgent(id, resume = false)
            request.prompt?.let { prompt -> firstPrompt(id, prompt) }
        }
        return id
    }

    override suspend fun branches(
        repo: String,
        machine: String?,
    ) = machines[machine].workspace.branches(repo)

    override suspend fun forkTask(taskId: TaskId): TaskId {
        val parent = view(taskId).task
        val machine = machines[parent.machine]
        val adapter = machine.adapter(parent.agent)
        terms.requireAcknowledged(adapter)
        val id = uniqueId()
        val title = "${parent.title} (fork)"
        val slug = slug(title, id)
        val worktree = machine.worktree(parent.repo, slug)
        val branch = "$BRANCH_PREFIX$slug"
        machine.workspace.forkWorktree(parent.repo, parent.worktree, worktree, branch)
        val forkFrom = parent.sessionRef.takeIf { Capability.Fork in adapter.capabilities }
        val native = forkFrom != null
        val task =
            Task(
                id,
                title,
                parent.repo,
                worktree,
                branch,
                parent.baseRef,
                parent.agent,
                parent.options,
                machine = parent.machine,
                permissionMode = parent.permissionMode,
                handoffFrom = if (native) parent.handoffFrom else parent.handoffFrom ?: adapter.displayName,
                parentId = parent.id,
            )
        mutableState.update { it.copy(tasks = it.tasks + TaskView(task, AgentStatus.Starting)) }
        transcripts.update { it + (id to it[taskId].orEmpty()) }
        val how = if (native) "" else "; the next prompt carries the conversation over"
        note(id, TranscriptEntry.Notice("Forked from \"${parent.title}\"$how"))
        withTask(id) { if (setUp(id)) startAgent(id, resume = false, forkFrom = forkFrom) }
        return id
    }

    // The output is already in the setup entry right above the failure notice.
    private suspend fun setUp(id: TaskId): Boolean {
        val failure = setup.run(view(id).task) ?: return true
        apply(id, MutableStateFlow(AgentState.Initial), AgentEvent.Failed(failure))
        return false
    }

    private suspend fun firstPrompt(
        id: TaskId,
        prompt: String,
    ) {
        // A queued task keeps the prompt in its message queue until its agent starts.
        if (running(id) == null && view(id).status != AgentStatus.Queued) {
            note(
                id,
                TranscriptEntry.Notice("The first prompt was not sent because the agent did not start", error = true),
            )
        } else {
            sendLocked(id, prompt)
        }
    }

    override suspend fun sendPrompt(
        taskId: TaskId,
        text: String,
    ) {
        withTask(taskId) { sendLocked(taskId, text) }
    }

    private suspend fun sendLocked(
        id: TaskId,
        text: String,
    ) {
        if (view(id).status == AgentStatus.Queued) return queues.add(id, text)
        val runtime = running(id) ?: throw TaskNotRunningException(id)
        when (view(id).status) {
            AgentStatus.Working, AgentStatus.Blocked -> if (!steer(id, runtime, text)) queues.add(id, text)
            else -> prompt(id, runtime, text)
        }
    }

    private suspend fun prompt(
        id: TaskId,
        runtime: Runtime,
        text: String,
    ) {
        // Built before the new message lands, so the handoff ends with it exactly once.
        val task = view(id).task
        val from = task.handoffFrom
        val handedOver = from?.let { Handoff.build(transcripts.value[id].orEmpty(), text, it) } ?: text
        val sent = task.revertNote?.let { "$it\n\n$handedOver" } ?: handedOver
        // The first turn gets a starting point too, so it can be undone like any other.
        if (task.checkpoints.isEmpty()) checkpoints.record(id, runtime.run)
        note(id, TranscriptEntry.Text(Role.User, text))
        compaction?.cancel(id)
        startTurn(id, runtime) { runtime.session.prompt(sent) }
        updateView(id) { it.copy(task = it.task.copy(handoffFrom = null, revertNote = null)) }
        if (from != null) note(id, TranscriptEntry.Notice("Handed the conversation over from $from"))
    }

    // Whatever the agent throws, the turn did not start and the caller hears why.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun startTurn(
        id: TaskId,
        runtime: Runtime,
        send: suspend () -> Unit,
    ) {
        // Working before the agent says so: a sender in between queues instead of starting a
        // second turn the agent cannot take.
        apply(id, runtime.agent, AgentEvent.TurnStarted)
        try {
            send()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Back to idle without going through apply, which would send the next queued prompt
            // into the same failure.
            val reduced = runtime.agent.updateAndGet { it.reduce(AgentEvent.TurnEnded(StopReason.Cancelled)) }
            updateView(id) { it.copy(task = it.task.copy(lastStatus = reduced.status), status = reduced.status) }
            throw e
        }
    }

    // Checked again under the lock: the task may have moved on since its time was set. A failure
    // only costs the saving, so it is a notice.
    private suspend fun compactIdle(id: TaskId) =
        withTask(id) {
            val runtime = running(id)?.takeIf { Capability.Compact in it.capabilities }
            if (runtime == null || mutableState.value[id]?.let(IdleCompaction::worthIt) != true) return@withTask
            compaction?.started(id)
            note(id, TranscriptEntry.Notice("Compacting the idle conversation before its prompt cache expires"))
            runCatching { startTurn(id, runtime) { runtime.session.compact() } }.onFailure {
                if (it is CancellationException) throw it
                note(id, TranscriptEntry.Notice("Could not compact: ${it.message}", error = true))
            }
        }

    // Any failure means the turn could not take the text, and the queue is the declared fallback.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun steer(
        id: TaskId,
        runtime: Runtime,
        text: String,
    ): Boolean {
        val steered =
            Capability.Steer in runtime.capabilities &&
                try {
                    runtime.session.steer(text)
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    false
                }
        if (steered) note(id, TranscriptEntry.Text(Role.User, text))
        return steered
    }

    override suspend fun editQueued(
        taskId: TaskId,
        queuedId: Int,
        text: String,
    ) {
        require(text.isNotBlank()) { "The message is empty" }
        withTask(taskId) { queues.edit(taskId, queuedId, text) }
    }

    override suspend fun removeQueued(
        taskId: TaskId,
        queuedId: Int,
    ) {
        withTask(taskId) { queues.remove(taskId, queuedId) }
    }

    private fun deliverQueued(
        id: TaskId,
        runtime: Runtime,
    ) {
        if (view(id).task.queue.isEmpty()) return
        runtime.scope.launch { withTask(id) { sendQueued(id, runtime) } }
    }

    // The agent may have been stopped or prompted while this waited for the lock; then the next
    // idle sends the head instead.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun sendQueued(
        id: TaskId,
        runtime: Runtime,
    ) {
        if (running(id) !== runtime) return
        val head =
            view(id)
                .takeIf { it.status == AgentStatus.Idle }
                ?.task
                ?.queue
                ?.firstOrNull() ?: return
        queues.remove(id, head.id)
        try {
            prompt(id, runtime, head.text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Kept, so the user can send it again or remove it.
            queues.putBack(id, head)
            note(id, TranscriptEntry.Notice("A queued message was not sent: ${e.message}", error = true))
        }
    }

    override suspend fun respond(
        taskId: TaskId,
        requestId: String,
        response: RequestResponse,
    ) {
        (running(taskId) ?: throw TaskNotRunningException(taskId)).session.respond(requestId, response)
    }

    override suspend fun switchAgent(
        taskId: TaskId,
        agent: AgentId,
        model: String?,
    ) {
        withTask(taskId) {
            val task = view(taskId).task
            val target = machines[task.machine].adapter(agent)
            terms.requireAcknowledged(target)
            // Another agent's model name means nothing to this one, so the old choice never carries over.
            val options = task.options - MODEL_OPTION + listOfNotNull(model?.let { MODEL_OPTION to it })
            val runtime = running(taskId)
            val inPlace =
                agent == task.agent && model != null && runtime != null &&
                    Capability.ModelSelection in target.capabilities && switchModel(runtime, model)
            if (inPlace) {
                updateView(taskId) { it.copy(task = it.task.copy(options = options)) }
                note(taskId, TranscriptEntry.Notice("Model: $model"))
                return@withTask
            }
            stopLocked(taskId, keepSlot = true)
            handOver(taskId, "Switched to ${target.displayName}${model?.let { " ($it)" }.orEmpty()}") {
                it.copy(agent = agent, options = options)
            }
            startAgent(taskId, resume = false)
        }
    }

    override fun moveTargets(taskId: TaskId): List<String?> = move.targets(view(taskId).task)

    // Whatever stops the move, the task stays where it was and its agent comes back.
    @Suppress("TooGenericExceptionCaught")
    override suspend fun moveTask(
        taskId: TaskId,
        machine: String?,
    ) {
        withTask(taskId) {
            val task = view(taskId).task
            val repo = move.repo(task, machine)
            terms.requireAcknowledged(machines[machine].adapter(task.agent))
            val wasRunning = running(taskId) != null
            stopLocked(taskId)
            val moved =
                try {
                    move.move(task, machine, repo)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    note(taskId, TranscriptEntry.Notice("Not moved to ${where(machine)}: ${e.message}", error = true))
                    if (wasRunning) startAgent(taskId, resume = true)
                    throw e
                }
            checkpoints.forget(task)
            handOver(taskId, "Moved to ${where(machine)}") {
                it.copy(machine = machine, repo = moved.repo, worktree = moved.worktree, checkpoints = emptyList())
            }
            moved.leftBehind?.let { note(taskId, TranscriptEntry.Notice(it, error = true)) }
            if (setUp(taskId)) startAgent(taskId, resume = false)
        }
    }

    /** Starts the next session afresh after [change]: its first prompt carries the conversation over. */
    private fun handOver(
        id: TaskId,
        what: String,
        change: (Task) -> Task,
    ) {
        val task = view(id).task
        val from = task.handoffFrom ?: machines[task.machine].registry[task.agent]?.displayName ?: task.agent.value
        updateView(id) { it.copy(task = change(it.task).copy(sessionRef = null, handoffFrom = from)) }
        note(id, TranscriptEntry.Notice("$what; the next prompt carries the conversation over"))
    }

    // Whatever the reason an agent refuses, the model still arrives through a fresh session.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun switchModel(
        runtime: Runtime,
        model: String,
    ): Boolean =
        try {
            runtime.session.setModel(model)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }

    // Unsupported operations, errors from the agent and a dead process all leave the same outcome:
    // the mode waits for the next start.
    @Suppress("TooGenericExceptionCaught")
    override suspend fun setPermissionMode(
        taskId: TaskId,
        mode: PermissionMode,
    ) {
        withTask(taskId) {
            updateView(taskId) { it.copy(task = it.task.copy(permissionMode = mode)) }
            val runtime = running(taskId) ?: return@withTask
            val effective = effectiveMode(taskId) ?: return@withTask
            try {
                runtime.session.setPermissionMode(effective)
                note(taskId, TranscriptEntry.Notice("Permissions: ${effective.label}"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                note(
                    taskId,
                    TranscriptEntry.Notice(
                        "Permissions change to ${effective.label} on the next restart: ${e.message}",
                    ),
                )
            }
        }
    }

    override suspend fun cancel(taskId: TaskId) {
        (running(taskId) ?: throw TaskNotRunningException(taskId)).session.cancel()
    }

    override suspend fun stop(taskId: TaskId) {
        withTask(taskId) { stopLocked(taskId) }
    }

    override suspend fun restart(taskId: TaskId) {
        withTask(taskId) {
            stopLocked(taskId, keepSlot = true)
            startAgent(taskId, resume = true)
        }
    }

    override suspend fun startNow(taskId: TaskId) {
        withTask(taskId) { starts.startNow(taskId) }
    }

    override suspend fun removeTask(
        taskId: TaskId,
        deleteWorktree: Boolean,
        force: Boolean,
    ) {
        withTask(taskId) {
            val task = view(taskId).task
            // Looked up only when needed, so a task whose machine was removed can still be dropped.
            val workspace = if (deleteWorktree) machines[task.machine].workspace else null
            if (workspace != null && !force && workspace.isDirty(task.worktree)) {
                throw WorktreeDirtyException(taskId, task.worktree)
            }
            stopLocked(taskId)
            checkpoints.forget(task)
            workspace?.removeWorktree(task.repo, task.worktree, force)
            mutableState.update { state ->
                val layout =
                    state.layout.takeUnless { it.selectedTask == taskId } ?: state.layout.copy(selectedTask = null)
                state.copy(tasks = state.tasks.filterNot { it.task.id == taskId }, layout = layout)
            }
            transcripts.update { it - taskId }
        }
        locks.update { it - taskId }
    }

    override suspend fun diff(taskId: TaskId): TaskDiff {
        val task = view(taskId).task
        return machines[task.machine].workspace.diff(task.worktree, task.baseRef)
    }

    override suspend fun revert(
        taskId: TaskId,
        ref: String,
    ) {
        withTask(taskId) { running(taskId).let { checkpoints.revert(view(taskId), ref, it?.run, it?.session) } }
    }

    override suspend fun warn(warning: String) {
        mutableState.update { if (warning in it.warnings) it else it.copy(warnings = it.warnings + warning) }
    }

    override suspend fun updateLayout(layout: Layout) {
        mutableState.update { it.copy(layout = layout) }
    }

    override suspend fun markSeen(taskId: TaskId) {
        updateView(taskId) { it.copy(unseen = false) }
    }

    override suspend fun setPlace(
        taskId: TaskId,
        place: TaskPlace,
    ) {
        view(taskId)
        updateView(taskId) { it.copy(task = it.task.copy(place = place)) }
    }

    override val processUsage: Flow<Map<TaskId, ProcessUsage>> =
        processes?.usage(PROCESS_SAMPLE_MS) { localRoots(mutableState.value, runtimes.value) } ?: flowOf(emptyMap())

    /** Stops every agent and writes the state; owned by the app, not the UI, so it is not on [OctoCore]. */
    suspend fun close() {
        // Emptied first, so stopping one agent does not start a waiting one on the way out.
        starts.close().forEach { id -> updateView(id) { it.copy(status = AgentStatus.Stopped) } }
        mutableState.value.tasks.forEach { stop(it.task.id) }
        saver.close(mutableState.value, transcripts.value)
    }

    /** Starts the agent, or queues the task when its machine has no free slot. */
    private suspend fun startAgent(
        id: TaskId,
        resume: Boolean,
        // Never stored on the task, so a fork that fails to start cannot later resume its parent's session.
        forkFrom: AgentSessionRef? = null,
    ) {
        val start = Waiting(id, view(id).task.machine, resume, forkFrom)
        if (starts.start(start)) return
        updateView(id) { it.copy(status = AgentStatus.Queued, pending = emptyList(), failure = null) }
        val where = start.machine?.let { "'$it'" } ?: "This computer"
        note(
            id,
            TranscriptEntry.Notice(
                "Waiting for a free slot: $where already runs ${starts.limit(start.machine)} agents",
            ),
        )
    }

    /** A failure to start shows on the task as [AgentStatus.Failed] instead of throwing. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun launchAgent(
        id: TaskId,
        resume: Boolean,
        forkFrom: AgentSessionRef?,
    ) {
        val task = view(id).task
        val adapter = machines[task.machine].adapter(task.agent)
        terms.requireAcknowledged(adapter)
        val ref = forkFrom ?: task.sessionRef.takeIf { resume && Capability.Resume in adapter.capabilities }
        if (resume && task.sessionRef != null && ref == null) {
            note(id, TranscriptEntry.Notice("${adapter.displayName} cannot resume a session; started a fresh one"))
        }
        val agent = MutableStateFlow(AgentState.Initial)
        updateView(id) {
            it.copy(status = AgentStatus.Starting, pending = emptyList(), failure = null, stats = SessionStats())
        }
        val session =
            try {
                // Octo's MCP server listens on this machine's loopback, so a remote agent cannot reach it.
                val servers =
                    mcp?.takeIf { task.machine == null && Capability.Mcp in adapter.capabilities }?.servers(id)
                adapter.start(
                    SessionSpec(
                        task.worktree,
                        resume = ref,
                        options = task.options,
                        permissionMode = effectiveMode(id) ?: PermissionMode.Supervised,
                        mcpServers = servers.orEmpty(),
                        fork = forkFrom != null,
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                apply(id, agent, AgentEvent.Failed(e.message ?: "The agent did not start"))
                return
            }
        val runtime = Runtime(session, agent, adapter.capabilities, scope)
        runtime.job = runtime.scope.launch { collect(id, runtime) }
        runtimes.update { it + (id to runtime) }
    }

    // A broken event stream fails this task only.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun collect(
        id: TaskId,
        runtime: Runtime,
    ) {
        try {
            runtime.session.events.collect {
                // Taken while the task still reads as working, so no new turn changes files under it.
                if (it is AgentEvent.TurnEnded) checkpoints.record(id, runtime.run)
                if (it is AgentEvent.StatsUpdated) it.stats.promptCacheTtlMs?.let { ttl -> compaction?.cached(id, ttl) }
                if (apply(id, runtime.agent, it)) deliverQueued(id, runtime)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            apply(id, runtime.agent, AgentEvent.Failed(e.message ?: "The agent's event stream failed"))
        }
        // An agent that ended on its own frees its slot; a stop has taken it out of runtimes already.
        if (runtimes.value[id] === runtime) starts.free(id)
    }

    /** [keepSlot] holds on to the slot for a start right after, as a restart does. */
    private suspend fun stopLocked(
        id: TaskId,
        keepSlot: Boolean = false,
    ) {
        compaction?.cancel(id)
        val runtime = runtimes.value[id]
        if (runtime == null) {
            if (view(id).status == AgentStatus.Queued) updateView(id) { it.copy(status = AgentStatus.Stopped) }
            if (!keepSlot) starts.free(id)
            return
        }
        runtimes.update { it - id }
        runtime.agent.update { it.closing() }
        withContext(NonCancellable) {
            runtime.session.close()
            withTimeoutOrNull(STOP_TIMEOUT_MS) { runtime.job.join() } ?: runtime.job.cancel()
            runtime.supervisor.cancel()
        }
        // The stream may end without an exit event; a stop the user asked for still reads as stopped.
        val failed = view(id).status == AgentStatus.Failed
        if (!failed) updateView(id) { it.copy(status = AgentStatus.Stopped, pending = emptyList()) }
        // Freed once the process is gone, so the next agent never runs beside it over the limit.
        if (!keepSlot) starts.free(id)
    }

    /** Applies [event]; true when it made the agent idle. */
    private fun apply(
        id: TaskId,
        agent: MutableStateFlow<AgentState>,
        event: AgentEvent,
    ): Boolean {
        val before = agent.value.status
        val reduced = agent.updateAndGet { it.reduce(event) }
        transcripts.update { it + (id to Transcript.reduce(it[id].orEmpty(), event)) }
        val turnEnded = before != AgentStatus.Idle && reduced.status == AgentStatus.Idle
        updateView(id) { it.next(event, reduced).wokenBy(before, reduced.status) }
        return turnEnded
    }

    /** The task's mode as its agent can run it, saying so when that is a fallback; null without the adapter. */
    private fun effectiveMode(id: TaskId): PermissionMode? {
        val task = view(id).task
        val (mode, fallback) = machines[task.machine].effectiveMode(task) ?: return null
        fallback?.let { note(id, TranscriptEntry.Notice(it)) }
        return mode
    }

    private fun running(id: TaskId): Runtime? = runtimes.value[id]?.takeIf { it.job.isActive }

    private fun view(id: TaskId): TaskView = mutableState.value[id] ?: throw UnknownTaskException(id)

    private fun updateView(
        id: TaskId,
        change: (TaskView) -> TaskView,
    ) = mutableState.update { it.change(id, change) }

    private fun note(
        id: TaskId,
        entry: TranscriptEntry,
    ) {
        transcripts.update { it + (id to Transcript.add(it[id].orEmpty(), entry)) }
    }

    private suspend fun <T> withTask(
        id: TaskId,
        block: suspend () -> T,
    ): T = locks.updateAndGet { if (id in it) it else it + (id to Mutex()) }.getValue(id).withLock { block() }

    private fun uniqueId(): TaskId {
        val taken =
            mutableState.value.tasks
                .map { it.task.id }
                .toSet()
        return generateSequence { TaskId(newId()) }.first { it !in taken }
    }

    private suspend fun begin() {
        val loaded = saver.load()
        val tasks = loaded.state.tasks
        // Cold start: nothing is running yet; a restart resumes through the stored session ref.
        mutableState.value =
            OctoState(
                tasks = tasks.map { TaskView(it.task, AgentStatus.Stopped) },
                layout = loaded.state.layout,
                warnings = loaded.warnings,
            )
        transcripts.value = tasks.associate { it.task.id to it.transcript }
        saver.watch(scope, mutableState, transcripts)
        scope.launch { mutableState.wakeSnoozed(clock) }
        compaction?.let { scope.launch { it.run { id -> scope.launch { compactIdle(id) } } } }
    }

    companion object {
        const val BRANCH_PREFIX = "octo/"
        const val MAX_QUEUED = 20
        private const val STOP_TIMEOUT_MS = 10_000L
        private const val PROCESS_SAMPLE_MS = 2_000L

        /** Loads the saved state (every task comes back stopped) and starts saving changes. */
        suspend fun open(
            registry: AdapterRegistry,
            workspace: Workspace,
            store: StateStore,
            terms: TermsGate,
            worktrees: Path,
            scope: CoroutineScope,
            newId: () -> String = ::randomId,
            machines: Map<String, Machine> = emptyMap(),
            mcp: McpProvider? = null,
            processes: ProcessMonitor? = null,
            defaultPermissionMode: PermissionMode = PermissionMode.Supervised,
            /** A repository's top-level path to the command that prepares its new worktrees. */
            worktreeSetup: Map<String, String> = emptyMap(),
            /** Wall time, for snoozes that last until a given moment. */
            clock: Clock = Clock.System,
            /** Whether the `checkpoints` flag records the worktree around every turn. */
            checkpoints: Boolean = false,
            /** Live agents allowed per machine (null for this computer); a machine left out has no limit. */
            agentLimits: Map<String?, Int> = emptyMap(),
            /** A bare repository that carries a moving task between two remote machines. */
            relay: Path = Path(worktrees.parent ?: worktrees, "relay.git"),
            /** Whether the `compact-idle` flag compacts a large idle conversation before its cache expires. */
            compactIdle: Boolean = false,
        ): Orchestrator =
            Orchestrator(
                Machines(LocalMachine(registry, workspace, worktrees), machines),
                store,
                terms,
                scope,
                newId,
                mcp,
                processes,
                defaultPermissionMode,
                worktreeSetup.mapKeys { it.key.trimEnd('/') },
                clock,
                checkpoints,
                agentLimits,
                relay.toString(),
                compactIdle,
            ).also {
                it.begin()
            }
    }
}
