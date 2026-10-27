package octo.adapters.cursor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import octo.adapters.base.AcpAgentAdapter
import octo.adapters.base.AcpSession
import octo.adapters.base.ProcessAgentSession
import octo.core.agent.AgentIcon
import octo.core.agent.AgentId
import octo.core.agent.AuthState
import octo.core.agent.Capability
import octo.core.agent.SessionSpec
import octo.core.process.ProcessLauncher
import octo.core.process.ProcessSpec
import octo.core.process.RunningProcess
import octo.core.process.run
import kotlin.coroutines.CoroutineContext

/** Cursor CLI over its native ACP server, `cursor-agent acp`, plus its `cursor/` extension methods. */
class CursorAdapter(
    launcher: ProcessLauncher,
    context: CoroutineContext = Dispatchers.Default,
) : AcpAgentAdapter(launcher, context) {
    override val id = ID
    override val displayName = "Cursor"
    override val icon =
        AgentIcon(
            """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><rect width="24" height="24" rx="6" fill="#3B82F6"/><path d="M7 5l11 6-5 1.5L10.5 18z" fill="#fff"/></svg>""",
        )
    override val capabilities =
        setOf(
            Capability.Resume,
            Capability.Cancel,
            Capability.Permissions,
            Capability.Questions,
            Capability.Plan,
            Capability.Mcp,
        )

    // Not `agent`: Grok Build installs a binary with that name too.
    override val binary = "cursor-agent"
    override val verifiedVersion = "2026.09.23-86fc751"
    override val loginMethods = listOf("cursor_login")

    // `status` reports the CLI's own login state; Octo never reads Cursor's credentials.
    override suspend fun checkAuth(binaryPath: String): AuthState {
        val result =
            withTimeoutOrNull(AUTH_TIMEOUT_MS) {
                launcher.run(ProcessSpec(binaryPath, listOf("status")))
            } ?: return AuthState.Unknown
        val text = result.stdout + "\n" + result.stderr
        return when {
            text.contains("Not logged in", ignoreCase = true) -> AuthState.LoggedOut
            result.exitCode == 0 && text.contains("Logged in") -> AuthState.LoggedIn
            else -> AuthState.Unknown
        }
    }

    override fun buildArgs(spec: SessionSpec): List<String> = listOf("acp")

    override fun createSession(
        process: RunningProcess,
        spec: SessionSpec,
    ): ProcessAgentSession = CursorSession(process, context, acpConfig)

    companion object {
        val ID = AgentId("cursor")

        /** `agent`, `plan` or `ask`. */
        const val OPTION_MODE = AcpSession.OPTION_MODE

        private const val AUTH_TIMEOUT_MS = 10_000L
    }
}
