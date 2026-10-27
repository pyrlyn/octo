package octo.core.orchestrator

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.IOException
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
import octo.core.agent.ToolStatus
import octo.core.git.TaskDiff
import octo.core.git.Workspace
import octo.core.handoff.Handoff
import octo.core.messaging.McpProvider
import octo.core.persist.PersistedState
import octo.core.persist.StateStore
import octo.core.process.ProcessMonitor
import octo.core.process.ProcessResult
import octo.core.process.ProcessUsage
import octo.core.status.AgentState
import octo.core.status.AgentStatus
import octo.core.task.Layout
import octo.core.task.NewTask
import octo.core.task.OctoState
import octo.core.task.QueuedPrompt
import octo.core.task.Task
import octo.core.task.TaskId
import octo.core.task.TaskView
import octo.core.task.Transcript
import octo.core.task.TranscriptEntry
import octo.core.terms.TermsGate
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

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
    private val store: StateStore,
    private val terms: TermsGate,
    private val scope: CoroutineScope,
    private val newId: () -> String,
    private val mcp: McpProvider?,
    private val processes: ProcessMonitor?,
    private val defaultPermissionMode: PermissionMode,
    private val worktreeSetup: Map<String, String>,
) : OctoCore {
    private val mutableState = MutableStateFlow(OctoState())
    private val transcripts = MutableStateFlow<Map<TaskId, List<TranscriptEntry>>>(emptyMap())
    private val runtimes = MutableStateFlow<Map<TaskId, Runtime>>(emptyMap())
    private val locks = MutableStateFlow<Map<TaskId, Mutex>>(emptyMap())
    private var saver: Job? = null

    override val state: StateFlow<OctoState> = mutableState.asStateFlow()

    /** One running agent; [scope] is the task's own supervisor, ended when the agent stops. */
    private class Runtime(
        val session: AgentSession,
        val agent: MutableStateFlow<AgentState>,
        val capabilities: Set<Capability>,
        parent: CoroutineScope,
    ) {
        val supervisor = SupervisorJob(parent.coroutineContext[Job])
        val scope = CoroutineScope(parent.coroutineContext + supervisor)
        lateinit var job: Job
    }

    override fun transcript(taskId: TaskId): Flow<List<TranscriptEntry>> =
        transcripts
            .map {
                it[taskId].orEmpty()
            }.distinctUntilChanged()

    override suspend fun createTask(request: NewTask): TaskId {
        val machine = machines[request.machine]
        val adapter = machine.registry[request.agent] ?: throw UnknownAgentException(request.agent)
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
        val adapter = machine.registry[parent.agent] ?: throw UnknownAgentException(parent.agent)
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

    // Runs the repository's setup command in the task's new worktree. False, with the task Failed,
    // when it does not succeed, so the agent never starts on a half-prepared worktree; whatever the
    // command or the machine throws counts as that failure.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun setUp(id: TaskId): Boolean {
        val task = view(id).task
        val command = worktreeSetup[task.repo.trimEnd('/')] ?: return true
        val title = "Setup: $command"
        setupEntry(id, title, ToolStatus.InProgress)
        val result =
            try {
                withTimeoutOrNull(
                    SETUP_TIMEOUT_MS,
                ) { machines[task.machine].workspace.runSetup(task.worktree, command) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ProcessResult(-1, "", e.message ?: "The setup command did not run")
            }
        val output = result?.let { listOf(it.stdout, it.stderr).filter(String::isNotBlank).joinToString("\n") }
        val detail = output?.takeLast(MAX_SETUP_OUTPUT)?.ifBlank { null }
        val ok = result?.exitCode == 0
        setupEntry(id, title, if (ok) ToolStatus.Completed else ToolStatus.Failed, detail)
        if (!ok) {
            val why = result?.let { "exited with code ${it.exitCode}" } ?: "timed out"
            // The output is already in the setup entry right above the notice.
            apply(
                id,
                MutableStateFlow(AgentState.Initial),
                AgentEvent.Failed("The setup command $why; the agent was not started"),
            )
        }
        return ok
    }

    private fun setupEntry(
        id: TaskId,
        title: String,
        status: ToolStatus,
        detail: String? = null,
    ) {
        val event = AgentEvent.ToolCall(SETUP_TOOL_ID, title, "execute", status, detail)
        transcripts.update { it + (id to Transcript.reduce(it[id].orEmpty(), event)) }
    }

    private suspend fun firstPrompt(
        id: TaskId,
        prompt: String,
    ) {
        if (running(id) == null) {
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
        val runtime = running(id) ?: throw TaskNotRunningException(id)
        when (view(id).status) {
            AgentStatus.Working, AgentStatus.Blocked -> if (!steer(id, runtime, text)) enqueue(id, text)
            else -> prompt(id, runtime, text)
        }
    }

    // Whatever the agent throws, the turn did not start and the caller hears why.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun prompt(
        id: TaskId,
        runtime: Runtime,
        text: String,
    ) {
        // Built before the new message lands, so the handoff ends with it exactly once.
        val from = view(id).task.handoffFrom
        val sent = from?.let { Handoff.build(transcripts.value[id].orEmpty(), text, it) } ?: text
        note(id, TranscriptEntry.Text(Role.User, text))
        // Working before the agent says so: a sender in between queues instead of starting a
        // second turn the agent cannot take.
        apply(id, runtime.agent, AgentEvent.TurnStarted)
        try {
            runtime.session.prompt(sent)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Back to idle without going through apply, which would send the next queued prompt
            // into the same failure.
            val reduced = runtime.agent.updateAndGet { it.reduce(AgentEvent.TurnEnded(StopReason.Cancelled)) }
            updateView(id) { it.copy(task = it.task.copy(lastStatus = reduced.status), status = reduced.status) }
            throw e
        }
        if (from != null) {
            updateView(id) { it.copy(task = it.task.copy(handoffFrom = null)) }
            note(id, TranscriptEntry.Notice("Handed the conversation over from $from"))
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

    private fun enqueue(
        id: TaskId,
        text: String,
    ) {
        val queue = view(id).task.queue
        check(queue.size < MAX_QUEUED) { "Task '${id.value}' already has $MAX_QUEUED messages waiting" }
        val next = QueuedPrompt((queue.maxOfOrNull { it.id } ?: 0) + 1, text)
        updateQueue(id) { it + next }
    }

    override suspend fun editQueued(
        taskId: TaskId,
        queuedId: Int,
        text: String,
    ) {
        require(text.isNotBlank()) { "The message is empty" }
        withTask(taskId) {
            check(view(taskId).task.queue.any { it.id == queuedId }) { "That message was already sent" }
            updateQueue(taskId) { queue -> queue.map { if (it.id == queuedId) it.copy(text = text) else it } }
        }
    }

    override suspend fun removeQueued(
        taskId: TaskId,
        queuedId: Int,
    ) {
        withTask(taskId) { updateQueue(taskId) { queue -> queue.filterNot { it.id == queuedId } } }
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
        updateQueue(id) { queue -> queue.filterNot { it.id == head.id } }
        try {
            prompt(id, runtime, head.text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Kept, so the user can send it again or remove it.
            updateQueue(id) { listOf(head) + it }
            note(id, TranscriptEntry.Notice("A queued message was not sent: ${e.message}", error = true))
        }
    }

    private fun updateQueue(
        id: TaskId,
        change: (List<QueuedPrompt>) -> List<QueuedPrompt>,
    ) = updateView(id) { it.copy(task = it.task.copy(queue = change(it.task.queue))) }

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
            val registry = machines[task.machine].registry
            val target = registry[agent] ?: throw UnknownAgentException(agent)
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
            stopLocked(taskId)
            val from = task.handoffFrom ?: registry[task.agent]?.displayName ?: task.agent.value
            updateView(taskId) {
                it.copy(task = it.task.copy(agent = agent, options = options, sessionRef = null, handoffFrom = from))
            }
            val name = target.displayName + model?.let { " ($it)" }.orEmpty()
            note(taskId, TranscriptEntry.Notice("Switched to $name; the next prompt carries the conversation over"))
            startAgent(taskId, resume = false)
        }
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
            stopLocked(taskId)
            startAgent(taskId, resume = true)
        }
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

    override suspend fun updateLayout(layout: Layout) {
        mutableState.update { it.copy(layout = layout) }
    }

    override suspend fun markSeen(taskId: TaskId) {
        updateView(taskId) { it.copy(unseen = false) }
    }

    override val processUsage: Flow<Map<TaskId, ProcessUsage>> =
        processes?.let { monitor ->
            flow {
                while (true) {
                    // A remote task's local process is the ssh client, whose figures would mislead.
                    val local =
                        mutableState.value.tasks
                            .filter { it.task.machine == null }
                            .map { it.task.id }
                            .toSet()
                    val roots =
                        runtimes.value
                            .filterKeys { it in local }
                            .mapNotNull { (id, runtime) -> runtime.session.processId?.let { id to it } }
                            .toMap()
                    val sampled = monitor.sample(roots.values.toSet())
                    emit(roots.mapNotNull { (id, pid) -> sampled[pid]?.let { id to it } }.toMap())
                    delay(PROCESS_SAMPLE_MS)
                }
            }
        } ?: flowOf(emptyMap())

    /** Stops every agent and writes the state; owned by the app, not the UI, so it is not on [OctoCore]. */
    suspend fun close() {
        mutableState.value.tasks.forEach { stop(it.task.id) }
        saver?.cancelAndJoin()
        save(persistedState(mutableState.value, transcripts.value))
    }

    /** Starts the agent; a failure to start shows on the task as [AgentStatus.Failed] instead of throwing. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun startAgent(
        id: TaskId,
        resume: Boolean,
        // Never stored on the task, so a fork that fails to start cannot later resume its parent's session.
        forkFrom: AgentSessionRef? = null,
    ) {
        val task = view(id).task
        val adapter = machines[task.machine].registry[task.agent] ?: throw UnknownAgentException(task.agent)
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
            runtime.session.events.collect { if (apply(id, runtime.agent, it)) deliverQueued(id, runtime) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            apply(id, runtime.agent, AgentEvent.Failed(e.message ?: "The agent's event stream failed"))
        }
    }

    private suspend fun stopLocked(id: TaskId) {
        val runtime = runtimes.value[id] ?: return
        runtimes.update { it - id }
        runtime.agent.update { it.closing() }
        withContext(NonCancellable) {
            runtime.session.close()
            withTimeoutOrNull(STOP_TIMEOUT_MS) { runtime.job.join() } ?: runtime.job.cancel()
            runtime.supervisor.cancel()
        }
        // The stream may end without an exit event; a stop the user asked for still reads as stopped.
        if (view(id).status !=
            AgentStatus.Failed
        ) {
            updateView(id) { it.copy(status = AgentStatus.Stopped, pending = emptyList()) }
        }
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
        updateView(id) { it.next(event, reduced) }
        return before != AgentStatus.Idle && reduced.status == AgentStatus.Idle
    }

    /** The task's mode as its agent can run it, saying so when that is a fallback; null without the adapter. */
    private fun effectiveMode(id: TaskId): PermissionMode? {
        val task = view(id).task
        val adapter = machines[task.machine].registry[task.agent] ?: return null
        val effective = task.permissionMode.within(adapter.permissionModes)
        if (effective != task.permissionMode) {
            note(
                id,
                TranscriptEntry.Notice(
                    "${adapter.displayName} has no ${task.permissionMode.label} mode; it runs ${effective.label}",
                ),
            )
        }
        return effective
    }

    private fun running(id: TaskId): Runtime? = runtimes.value[id]?.takeIf { it.job.isActive }

    private fun view(id: TaskId): TaskView = mutableState.value[id] ?: throw UnknownTaskException(id)

    private fun updateView(
        id: TaskId,
        change: (TaskView) -> TaskView,
    ) {
        mutableState.update { state ->
            state.copy(
                tasks =
                    state.tasks.map {
                        if (it.task.id ==
                            id
                        ) {
                            change(it)
                        } else {
                            it
                        }
                    },
            )
        }
    }

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
        val loaded = store.load()
        val tasks = loaded.state.tasks
        // Cold start: nothing is running yet; a restart resumes through the stored session ref.
        mutableState.value =
            OctoState(
                tasks = tasks.map { TaskView(it.task, AgentStatus.Stopped) },
                layout = loaded.state.layout,
                warnings = loaded.warnings,
            )
        transcripts.value = tasks.associate { it.task.id to it.transcript }
        // Partial text is not persisted, so streaming alone never triggers a write; drop(1) keeps
        // the backups from rotating on every launch.
        saver =
            scope.launch {
                combine(mutableState, transcripts, ::persistedState)
                    .distinctUntilChanged()
                    .drop(1)
                    .conflate()
                    .collect { save(it) }
            }
    }

    private suspend fun save(state: PersistedState) {
        try {
            store.save(state)
        } catch (e: IOException) {
            // Fail open: the session keeps running; the UI shows why nothing is being saved.
            val warning = "Could not save state: ${e.message}"
            mutableState.update { if (warning in it.warnings) it else it.copy(warnings = it.warnings + warning) }
        }
    }

    companion object {
        const val BRANCH_PREFIX = "octo/"
        const val MAX_QUEUED = 20
        private const val SETUP_TIMEOUT_MS = 30 * 60_000L
        private const val MAX_SETUP_OUTPUT = 4_000
        private const val SETUP_TOOL_ID = "octo.setup"
        private const val STOP_TIMEOUT_MS = 10_000L
        private const val PROCESS_SAMPLE_MS = 2_000L
        private const val SLUG_LENGTH = 32
        private val NOT_SLUG = Regex("[^a-z0-9]+")

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
            ).also {
                it.begin()
            }

        private const val HEX = 16
        private const val ID_LENGTH = 8

        private fun randomId(): String =
            Random
                .nextInt()
                .toUInt()
                .toString(HEX)
                .padStart(ID_LENGTH, '0')

        internal fun slug(
            title: String,
            id: TaskId,
        ): String {
            val words =
                title
                    .lowercase()
                    .replace(NOT_SLUG, "-")
                    .trim('-')
                    .take(SLUG_LENGTH)
                    .trimEnd('-')
            return if (words.isEmpty()) id.value else "$words-${id.value}"
        }
    }
}
