package octo.adapters.codex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import octo.adapters.base.BaseAgentAdapter
import octo.adapters.base.ProcessAgentSession
import octo.core.agent.AgentId
import octo.core.agent.AuthState
import octo.core.agent.Capability
import octo.core.agent.SessionSpec
import octo.core.process.ProcessLauncher
import octo.core.process.ProcessSpec
import octo.core.process.RunningProcess
import octo.core.process.run
import kotlin.coroutines.CoroutineContext

/** Codex through `codex app-server`: JSON-RPC over stdio without the `jsonrpc` field. */
class CodexAdapter(
    launcher: ProcessLauncher,
    context: CoroutineContext = Dispatchers.Default,
    private val clientVersion: String = "0.1.0",
) : BaseAgentAdapter(launcher, context) {
    override val id = ID
    override val displayName = "Codex"
    override val capabilities =
        setOf(
            Capability.Resume,
            Capability.Cancel,
            Capability.Permissions,
            Capability.Questions,
            Capability.Plan,
            Capability.ModelSelection,
        )
    override val binary = "codex"
    override val verifiedVersion = "0.162.1"

    // `login status` reports the CLI's own login state; Octo never reads `auth.json`.
    override suspend fun checkAuth(binaryPath: String): AuthState {
        val result =
            withTimeoutOrNull(AUTH_TIMEOUT_MS) {
                launcher.run(ProcessSpec(binaryPath, listOf("login", "status")))
            } ?: return AuthState.Unknown
        val text = result.stdout + "\n" + result.stderr
        return when {
            result.exitCode == 0 && text.contains("Logged in") -> AuthState.LoggedIn
            text.contains("Not logged in") -> AuthState.LoggedOut
            else -> AuthState.Unknown
        }
    }

    override fun buildArgs(spec: SessionSpec): List<String> = listOf("app-server")

    override fun createSession(
        process: RunningProcess,
        spec: SessionSpec,
    ): ProcessAgentSession = CodexSession(process, context, clientVersion)

    companion object {
        val ID = AgentId("codex")
        const val OPTION_MODEL = "model"

        /** `untrusted`, `on-request` (default) or `never`. */
        const val OPTION_APPROVAL_POLICY = "approvalPolicy"

        /** `read-only`, `workspace-write` (default) or `danger-full-access`. */
        const val OPTION_SANDBOX = "sandbox"

        private const val AUTH_TIMEOUT_MS = 10_000L
    }
}
