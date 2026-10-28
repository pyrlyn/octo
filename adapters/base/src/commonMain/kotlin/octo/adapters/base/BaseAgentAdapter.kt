package octo.adapters.base

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import octo.core.agent.AgentSession
import octo.core.agent.SessionSpec
import octo.core.process.ProcessLauncher
import octo.core.process.ProcessSpec
import octo.core.process.RunningProcess
import kotlin.coroutines.CoroutineContext

/** A CLI whose whole session is one long-running process. */
abstract class BaseAgentAdapter(
    launcher: ProcessLauncher,
    context: CoroutineContext = Dispatchers.Default,
) : CliAgentAdapter(launcher, context) {
    protected abstract fun buildArgs(spec: SessionSpec): List<String>

    protected open fun buildEnv(spec: SessionSpec): Map<String, String> = spec.env

    protected abstract fun createSession(
        process: RunningProcess,
        spec: SessionSpec,
    ): ProcessAgentSession

    override suspend fun start(spec: SessionSpec): AgentSession {
        val path = binaryPath()
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
}
