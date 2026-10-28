package octo.core.orchestrator

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.io.files.Path
import octo.core.agent.AdapterRegistry
import octo.core.agent.AgentAdapter
import octo.core.agent.AgentEvent
import octo.core.agent.AgentId
import octo.core.agent.AgentProbe
import octo.core.agent.AgentSession
import octo.core.agent.AgentSessionRef
import octo.core.agent.AuthState
import octo.core.agent.Capability
import octo.core.agent.PermissionMode
import octo.core.agent.RequestResponse
import octo.core.agent.SessionSpec
import octo.core.agent.VendorTerms
import octo.core.flags.FeatureFlags
import octo.core.git.GitRemote
import octo.core.git.RefTransfer
import octo.core.git.TaskDiff
import octo.core.git.Workspace
import octo.core.io.AtomicFile
import octo.core.messaging.McpProvider
import octo.core.persist.StateStore
import octo.core.process.ProcessLauncher
import octo.core.process.ProcessMonitor
import octo.core.process.ProcessResult
import octo.core.process.ProcessSpec
import octo.core.process.RunningProcess
import octo.core.tempDir
import octo.core.terms.TermsGate
import kotlin.time.Clock
import kotlin.time.Instant

internal class FakeSession(
    override val processId: Long? = null,
    private val refuseModel: Boolean = false,
) : AgentSession {
    val channel = Channel<AgentEvent>(Channel.UNLIMITED)
    val prompts = mutableListOf<String>()
    val models = mutableListOf<String>()
    val steered = mutableListOf<String>()
    var refuseSteer = false
    val responses = mutableListOf<Pair<String, RequestResponse>>()
    var cancelled = 0
    val modes = mutableListOf<PermissionMode>()
    var refuseMode = false
    val rollbacks = mutableListOf<Int>()
    var refuseRollback = false

    override val events: Flow<AgentEvent> = channel.receiveAsFlow()

    fun emit(vararg events: AgentEvent) = events.forEach { channel.trySend(it) }

    override suspend fun prompt(text: String) {
        prompts += text
    }

    override suspend fun steer(text: String) {
        check(!refuseSteer) { "the turn ended" }
        steered += text
    }

    override suspend fun respond(
        requestId: String,
        response: RequestResponse,
    ) {
        responses += requestId to response
    }

    override suspend fun setPermissionMode(mode: PermissionMode) {
        check(!refuseMode) { "fake cannot switch" }
        modes += mode
    }

    override suspend fun rollback(turns: Int) {
        check(!refuseRollback) { "fake cannot roll back" }
        rollbacks += turns
    }

    override suspend fun cancel() {
        cancelled++
    }

    override suspend fun setModel(model: String) {
        if (refuseModel) error("no live model switch")
        models += model
    }

    override suspend fun close() {
        channel.trySend(AgentEvent.Exited(0))
        channel.close()
    }
}

internal class FakeAdapter(
    override val capabilities: Set<Capability> = setOf(Capability.Resume),
    override val terms: VendorTerms? = null,
    var failStart: Boolean = false,
    /** The process id each new session reports. */
    var pid: Long? = null,
    override val id: AgentId = AgentId("fake"),
    override val displayName: String = "Fake",
    private val refuseModel: Boolean = false,
    override val permissionModes: Set<PermissionMode> = setOf(PermissionMode.Supervised),
) : AgentAdapter {
    val specs = mutableListOf<SessionSpec>()
    val sessions = mutableListOf<FakeSession>()

    override suspend fun probe() = AgentProbe("/bin/fake", "1", "1", AuthState.Unknown)

    override suspend fun start(spec: SessionSpec): AgentSession {
        if (failStart) error("fake is not installed")
        specs += spec
        return FakeSession(pid, refuseModel).also {
            val ref = spec.resume.takeUnless { spec.fork } ?: AgentSessionRef("s${sessions.size + 1}")
            it.emit(AgentEvent.SessionReady(ref))
            sessions += it
        }
    }
}

internal class FakeWorkspace :
    Workspace,
    RefTransfer {
    val worktrees = mutableMapOf<String, String>()
    val removed = mutableListOf<String>()

    /** Source worktree to the fork's path. */
    val forks = mutableListOf<Pair<String, String>>()
    val dirty = mutableSetOf<String>()

    /** Worktree and command of each setup run. */
    val setups = mutableListOf<Pair<String, String>>()

    /** What a setup run returns; one that never returns stands in for a hung command. */
    var setupResult: suspend () -> ProcessResult = { ProcessResult(0, "installed", "") }

    /** Every directory is in this one repository. */
    var root = "/src/app"

    override suspend fun repoRoot(dir: String) = root

    override suspend fun branches(repo: String) = listOf("main")

    override suspend fun createWorktree(
        repo: String,
        path: String,
        branch: String,
        baseRef: String,
        takeOver: Boolean,
    ) {
        worktrees[path] = branch
        bases[path] = baseRef
    }

    /** Each worktree's start point. */
    val bases = mutableMapOf<String, String>()

    override suspend fun forkWorktree(
        repo: String,
        source: String,
        path: String,
        branch: String,
    ) {
        forks += source to path
        worktrees[path] = branch
    }

    override suspend fun runSetup(
        worktree: String,
        command: String,
    ): ProcessResult {
        setups += worktree to command
        return setupResult()
    }

    override suspend fun isDirty(worktree: String) = worktree in dirty

    override suspend fun removeWorktree(
        repo: String,
        worktree: String,
        force: Boolean,
    ) {
        removed += worktree
    }

    override suspend fun diff(
        worktree: String,
        baseRef: String,
    ) = TaskDiff(baseRef, "abc", emptyList())

    /** A worktree's "files", which a checkpoint copies and a restore puts back. */
    val files = mutableMapOf<String, String>()

    /** Refs to the files they hold. */
    val refs = mutableMapOf<String, String>()
    var failSnapshot = false

    override suspend fun snapshot(
        worktree: String,
        ref: String,
    ) {
        check(!failSnapshot) { "disk full" }
        refs[ref] = files[worktree].orEmpty()
    }

    override suspend fun restore(
        worktree: String,
        ref: String,
    ) {
        files[worktree] = refs.getValue(ref)
    }

    override suspend fun deleteRefs(
        repo: String,
        ref: String,
    ) {
        refs.keys.removeAll { it == ref || it.startsWith("$ref/") }
    }

    /** The other machines' workspaces, by the URL git reaches them at. */
    val peers = mutableMapOf<String, FakeWorkspace>()
    val bares = mutableListOf<String>()
    var failTransfer = false

    override suspend fun pushRefs(
        repo: String,
        remote: GitRemote,
        refs: List<String>,
    ) {
        check(!failTransfer) { "Connection refused" }
        refs.forEach { peers.getValue(remote.url).refs[it] = this.refs.getValue(it) }
    }

    override suspend fun fetchRefs(
        repo: String,
        remote: GitRemote,
        refs: List<String>,
    ) {
        check(!failTransfer) { "Connection refused" }
        refs.forEach { this.refs[it] = peers.getValue(remote.url).refs.getValue(it) }
    }

    override suspend fun initBare(path: String) {
        bares += path
    }
}

/** A remote machine's launcher for tests whose agents start through a fake adapter. */
internal object NoProcesses : ProcessLauncher {
    override suspend fun which(command: String): String? = null

    override suspend fun launch(spec: ProcessSpec): RunningProcess = error("not used")
}

internal class Setup(
    val core: Orchestrator,
    val adapter: FakeAdapter,
    val workspace: FakeWorkspace,
    val home: Path,
)

@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun TestScope.setup(
    adapter: FakeAdapter = FakeAdapter(),
    workspace: FakeWorkspace = FakeWorkspace(),
    home: Path = tempDir("octo-core"),
    machines: Map<String, Machine> = emptyMap(),
    newId: () -> String = { "id1" },
    mcp: McpProvider? = null,
    processes: ProcessMonitor? = null,
    /** More agents next to [adapter], for tests that switch between them. */
    others: List<AgentAdapter> = emptyList(),
    defaultPermissionMode: PermissionMode = PermissionMode.Supervised,
    worktreeSetup: Map<String, String> = emptyMap(),
    /** The test scheduler's virtual time, so a snooze runs out on `advanceTimeBy`. */
    clock: Clock =
        object : Clock {
            override fun now() = Instant.fromEpochMilliseconds(testScheduler.currentTime)
        },
    checkpoints: Boolean = false,
    agentLimits: Map<String?, Int> = emptyMap(),
): Setup {
    val core =
        Orchestrator.open(
            registry = AdapterRegistry(listOf(adapter) + others, FeatureFlags()),
            workspace = workspace,
            store = StateStore(Path(home, "state.json")),
            terms = TermsGate(AtomicFile(Path(home, "acknowledgements.json"))),
            worktrees = Path(home, "worktrees"),
            scope = backgroundScope,
            newId = newId,
            machines = machines,
            mcp = mcp,
            processes = processes,
            defaultPermissionMode = defaultPermissionMode,
            worktreeSetup = worktreeSetup,
            clock = clock,
            checkpoints = checkpoints,
            agentLimits = agentLimits,
        )
    return Setup(core, adapter, workspace, home)
}
