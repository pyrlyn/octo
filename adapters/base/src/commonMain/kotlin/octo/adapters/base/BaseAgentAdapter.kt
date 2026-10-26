package octo.adapters.base

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import octo.core.agent.AgentAdapter
import octo.core.agent.AgentNotInstalledException
import octo.core.agent.AgentProbe
import octo.core.agent.AgentSession
import octo.core.agent.AuthState
import octo.core.agent.SessionSpec
import octo.core.process.ProcessLauncher
import octo.core.process.ProcessSpec
import octo.core.process.RunningProcess
import octo.core.process.run
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Spawns the user's own installed CLI. Octo never reads or passes agent credentials: the CLI
 * uses whatever login the user already has.
 */
abstract class BaseAgentAdapter(
    protected val launcher: ProcessLauncher,
    protected val context: CoroutineContext = Dispatchers.Default,
) : AgentAdapter {
    protected abstract val binary: String

    /** The version this adapter's protocol mapping was checked against. */
    protected abstract val verifiedVersion: String

    protected open val versionArgs: List<String> = listOf("--version")

    protected open fun parseVersion(output: String): String? = VERSION.find(output)?.value

    /** Override when the CLI can report its login state without a model call. */
    protected open suspend fun checkAuth(binaryPath: String): AuthState = AuthState.Unknown

    protected abstract fun buildArgs(spec: SessionSpec): List<String>

    protected open fun buildEnv(spec: SessionSpec): Map<String, String> = spec.env

    protected abstract fun createSession(
        process: RunningProcess,
        spec: SessionSpec,
    ): ProcessAgentSession

    // A broken or hanging CLI must show up as "unknown version", not break the agent list.
    @Suppress("TooGenericExceptionCaught")
    override suspend fun probe(): AgentProbe {
        val path = launcher.which(binary) ?: return AgentProbe(null, null, verifiedVersion, AuthState.Unknown)
        val version =
            try {
                withTimeoutOrNull(PROBE_TIMEOUT_MS) { launcher.run(ProcessSpec(path, versionArgs)) }
                    ?.let { parseVersion(it.stdout) ?: parseVersion(it.stderr) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        return AgentProbe(path, version, verifiedVersion, checkAuth(path))
    }

    override suspend fun start(spec: SessionSpec): AgentSession {
        val path = launcher.which(binary) ?: throw AgentNotInstalledException(id)
        val process = launcher.launch(ProcessSpec(path, buildArgs(spec), spec.workingDir, buildEnv(spec)))
        val session = createSession(process, spec)
        session.start()
        var ready = false
        try {
            session.initialize(spec)
            ready = true
        } finally {
            // A failed handshake must not leave an orphaned agent process behind.
            if (!ready) withContext(NonCancellable) { session.close() }
        }
        return session
    }

    private companion object {
        val VERSION = Regex("""\d+\.\d+(\.\d+)?([-+.][0-9A-Za-z.-]+)?""")
        const val PROBE_TIMEOUT_MS = 10_000L
    }
}
