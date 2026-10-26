package octo.core.orchestrator

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.files.Path
import octo.core.agent.AdapterRegistry
import octo.core.git.Workspace
import octo.core.process.ProcessLauncher
import octo.core.process.ProcessSpec
import octo.core.process.run

/** Where a task runs: its agents, its git and the root its worktrees go under. */
interface Machine {
    val registry: AdapterRegistry
    val workspace: Workspace

    /** The worktree path for [slug] of [repo], on this machine. */
    suspend fun worktree(
        repo: String,
        slug: String,
    ): String
}

/** This computer. */
class LocalMachine(
    override val registry: AdapterRegistry,
    override val workspace: Workspace,
    private val worktrees: Path,
) : Machine {
    override suspend fun worktree(
        repo: String,
        slug: String,
    ) = Path(worktrees, Path(repo).name, slug).toString()
}

/**
 * A POSIX machine reached through [launcher]. Its paths always use `/`, whatever this computer
 * uses. Without a configured root, worktrees go under `$HOME/.octo/worktrees` there, looked up on
 * first use so start-up never waits on the network.
 */
class RemoteMachine(
    override val registry: AdapterRegistry,
    override val workspace: Workspace,
    private val launcher: ProcessLauncher,
    private val worktrees: String? = null,
) : Machine {
    private val lock = Mutex()
    private var root: String? = worktrees

    override suspend fun worktree(
        repo: String,
        slug: String,
    ) = "${root()}/${repo.trimEnd('/').substringAfterLast('/')}/$slug"

    private suspend fun root(): String =
        lock.withLock {
            root ?: "${home()}/.octo/worktrees".also { root = it }
        }

    private suspend fun home(): String {
        val home =
            launcher
                .run(ProcessSpec("printenv", listOf("HOME")))
                .stdout
                .trim()
                .trimEnd('/')
        check(home.startsWith("/")) { "Could not find the home directory on the remote machine" }
        return home
    }
}

/** This computer plus the remote machines, found by a task's machine id (null for this computer). */
class Machines(
    private val local: Machine,
    /** Empty unless the `remote-ssh` flag is on. */
    private val remote: Map<String, Machine>,
) {
    operator fun get(id: String?): Machine = if (id == null) local else remote[id] ?: throw UnknownMachineException(id)
}

class UnknownMachineException(
    id: String,
) : IllegalArgumentException("Machine '$id' is not configured or the remote-ssh flag is off")
