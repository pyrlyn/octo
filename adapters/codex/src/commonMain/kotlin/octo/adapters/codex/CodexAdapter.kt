package octo.adapters.codex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.files.Path
import octo.adapters.base.BaseAgentAdapter
import octo.adapters.base.ProcessAgentSession
import octo.core.agent.AgentIcon
import octo.core.agent.AgentId
import octo.core.agent.AuthState
import octo.core.agent.Capability
import octo.core.agent.MODEL_OPTION
import octo.core.agent.PermissionMode
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
    /** Codex's home (`CODEX_HOME`, else `~/.codex`); null reads no history. */
    home: Path? = null,
) : BaseAgentAdapter(launcher, context) {
    override val id = ID
    override val displayName = "Codex"
    override val icon =
        AgentIcon(
            """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><rect width="24" height="24" rx="6" fill="#4E8C7E"/><g fill="none" stroke="#fff" stroke-width="1.2"><rect x="9.7" y="7.4" width="4.6" height="9.2" rx="2.3" transform="rotate(0 12 12) translate(3.4 0.9)"/><rect x="9.7" y="7.4" width="4.6" height="9.2" rx="2.3" transform="rotate(60 12 12) translate(3.4 0.9)"/><rect x="9.7" y="7.4" width="4.6" height="9.2" rx="2.3" transform="rotate(120 12 12) translate(3.4 0.9)"/><rect x="9.7" y="7.4" width="4.6" height="9.2" rx="2.3" transform="rotate(180 12 12) translate(3.4 0.9)"/><rect x="9.7" y="7.4" width="4.6" height="9.2" rx="2.3" transform="rotate(240 12 12) translate(3.4 0.9)"/><rect x="9.7" y="7.4" width="4.6" height="9.2" rx="2.3" transform="rotate(300 12 12) translate(3.4 0.9)"/></g></svg>""",
        )
    override val capabilities =
        setOf(
            Capability.Resume,
            Capability.Cancel,
            Capability.Permissions,
            Capability.Questions,
            Capability.Plan,
            Capability.ModelSelection,
            Capability.Fork,
            Capability.Steer,
        )
    override val permissionModes = PermissionMode.entries.toSet()
    override val usageHistory = home?.let(::codexHistory)
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
        const val OPTION_MODEL = MODEL_OPTION

        private const val AUTH_TIMEOUT_MS = 10_000L
    }
}
