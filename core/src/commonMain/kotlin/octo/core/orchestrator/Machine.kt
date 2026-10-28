package octo.core.orchestrator

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.files.Path
import octo.core.agent.AdapterRegistry
import octo.core.agent.AgentId
import octo.core.agent.PermissionMode
import octo.core.attachments.AttachmentStore
import octo.core.attachments.LocalAttachments
import octo.core.attachments.RemoteAttachments
import octo.core.git.GitRemote
import octo.core.git.GitRemotes
import octo.core.git.Workspace
import octo.core.process.ProcessLauncher
import octo.core.process.ProcessSpec
import octo.core.process.run
import octo.core.task.Task

/** Where a task runs: its agents, its git and the root its worktrees go under. */
interface Machine {
    val registry: AdapterRegistry
    val workspace: Workspace

    /** The files attached to messages of the tasks that run here. */
    val attachments: AttachmentStore

    /** The worktree path for [slug] of [repo], on this machine. */
    suspend fun worktree(
        repo: String,
        slug: String,
    ): String

    /** This machine's clone of the repository at [local] on this computer; null when it has none. */
    fun repo(local: String): String?

    /** The path on this computer of the repository whose clone here is at [repo]; null when unknown. */
    fun local(repo: String): String?

    /** Where git on another machine reaches [repo] here; null for this computer, which git reaches by path. */
    suspend fun gitRemote(repo: String): GitRemote? = null
}

/** This computer. */
class LocalMachine(
    override val registry: AdapterRegistry,
    override val workspace: Workspace,
    private val worktrees: Path,
    override val attachments: AttachmentStore = LocalAttachments(Path(worktrees.parent ?: worktrees, "attachments")),
) : Machine {
    override suspend fun worktree(
        repo: String,
        slug: String,
    ) = Path(worktrees, Path(repo).name, slug).toString()

    override fun repo(local: String) = local

    override fun local(repo: String) = repo
}

/**
 * A POSIX machine reached through [launcher]. Its paths always use `/`, whatever this computer
 * uses. Without a configured root, worktrees go under `$HOME/.octo/worktrees` there, looked up on
 * first use so start-up never waits on the network; attachments always go under
 * `$HOME/.octo/attachments`.
 */
class RemoteMachine(
    override val registry: AdapterRegistry,
    override val workspace: Workspace,
    private val launcher: ProcessLauncher,
    private val worktrees: String? = null,
    /** Repository paths on this computer to their clones here, from the machine's `repos` in config. */
    repos: Map<String, String> = emptyMap(),
    private val remotes: GitRemotes? = null,
) : Machine {
    private val repos = repos.entries.associate { (local, here) -> local.trimEnd('/') to here.trimEnd('/') }
    private val lock = Mutex()
    private var root: String? = worktrees
    private var home: String? = null

    override val attachments: AttachmentStore = RemoteAttachments(launcher) { "${home()}/.octo/attachments" }

    override suspend fun worktree(
        repo: String,
        slug: String,
    ) = "${root()}/${repo.trimEnd('/').substringAfterLast('/')}/$slug"

    override fun repo(local: String) = repos[local.trimEnd('/')]

    override fun local(repo: String) = repos.entries.firstOrNull { it.value == repo.trimEnd('/') }?.key

    override suspend fun gitRemote(repo: String) = remotes?.at(repo)

    private suspend fun root(): String = root ?: "${home()}/.octo/worktrees".also { root = it }

    private suspend fun home(): String =
        lock.withLock {
            home ?: launcher
                .run(ProcessSpec("printenv", listOf("HOME")))
                .stdout
                .trim()
                .trimEnd('/')
                .also { check(it.startsWith("/")) { "Could not find the home directory on the remote machine" } }
                .also { home = it }
        }
}

/** This computer plus the remote machines, found by a task's machine id (null for this computer). */
class Machines(
    private val local: Machine,
    /** Empty unless the `remote-ssh` flag is on. */
    private val remote: Map<String, Machine>,
) {
    operator fun get(id: String?): Machine = if (id == null) local else remote[id] ?: throw UnknownMachineException(id)

    /** Every machine's id, null for this computer first. */
    val ids: List<String?> get() = listOf(null) + remote.keys
}

class UnknownMachineException(
    id: String,
) : IllegalArgumentException("Machine '$id' is not configured or the remote-ssh flag is off")

/** How notices name [machine]: its id, or this computer. */
internal fun where(machine: String?) = machine?.let { "'$it'" } ?: "this computer"

/** The adapter for [agent] on this machine; throws when it has none. */
internal fun Machine.adapter(agent: AgentId) = registry[agent] ?: throw UnknownAgentException(agent)

/** [task]'s mode as its agent here can run it, with a notice when that is a fallback; null without the adapter. */
internal fun Machine.effectiveMode(task: Task): Pair<PermissionMode, String?>? {
    val adapter = registry[task.agent] ?: return null
    val effective = task.permissionMode.within(adapter.permissionModes)
    val fallback = "${adapter.displayName} has no ${task.permissionMode.label} mode; it runs ${effective.label}"
    return effective to fallback.takeIf { effective != task.permissionMode }
}
