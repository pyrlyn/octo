package octo.adapters.claude

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import octo.adapters.base.BaseAgentAdapter
import octo.adapters.base.ProcessAgentSession
import octo.adapters.base.bool
import octo.adapters.base.parseJsonObject
import octo.core.agent.AgentId
import octo.core.agent.AgentSession
import octo.core.agent.AgentSessionRef
import octo.core.agent.AuthState
import octo.core.agent.Capability
import octo.core.agent.SessionSpec
import octo.core.process.ProcessLauncher
import octo.core.process.ProcessSpec
import octo.core.process.RunningProcess
import octo.core.process.run
import kotlin.coroutines.CoroutineContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Claude Code in headless mode: stream-json in and out, with permission prompts and questions
 * routed to Octo over the stdin control protocol (`--permission-prompt-tool stdio`).
 */
class ClaudeAdapter(
    launcher: ProcessLauncher,
    context: CoroutineContext = Dispatchers.Default,
    private val newSessionId: () -> String = ::randomSessionId,
) : BaseAgentAdapter(launcher, context) {
    override val id = ID
    override val displayName = "Claude"
    override val capabilities =
        setOf(
            Capability.Resume,
            Capability.Cancel,
            Capability.Permissions,
            Capability.Questions,
            Capability.Plan,
            Capability.ModelSelection,
        )
    override val binary = "claude"
    override val verifiedVersion = "2.1.310"

    // `auth status` reports the CLI's own login state; Octo never reads the credentials.
    override suspend fun checkAuth(binaryPath: String): AuthState {
        val result =
            withTimeoutOrNull(AUTH_TIMEOUT_MS) {
                launcher.run(ProcessSpec(binaryPath, listOf("auth", "status", "--json")))
            } ?: return AuthState.Unknown
        return when (parseJsonObject(result.stdout)?.bool("loggedIn")) {
            true -> AuthState.LoggedIn
            false -> AuthState.LoggedOut
            null -> AuthState.Unknown
        }
    }

    override fun buildArgs(spec: SessionSpec): List<String> =
        buildList {
            addAll(BASE_ARGS)
            // The `--flag=value` form keeps a value that starts with a dash from being read as a flag.
            spec.options[OPTION_MODEL]?.let { add("--model=$it") }
            spec.options[OPTION_PERMISSION_MODE]?.let { add("--permission-mode=$it") }
            add(spec.resume?.let { "--resume=${it.value}" } ?: "--session-id=${spec.options.getValue(SESSION_ID)}")
        }

    // A new session gets its id from Octo, so the ref is known before Claude prints anything.
    override suspend fun start(spec: SessionSpec): AgentSession =
        super.start(
            if (spec.resume ==
                null
            ) {
                spec.copy(options = spec.options + (SESSION_ID to newSessionId()))
            } else {
                spec
            },
        )

    override fun createSession(
        process: RunningProcess,
        spec: SessionSpec,
    ): ProcessAgentSession =
        ClaudeSession(process, context, spec.resume ?: AgentSessionRef(spec.options.getValue(SESSION_ID)))

    companion object {
        val ID = AgentId("claude")
        const val OPTION_MODEL = "model"
        const val OPTION_PERMISSION_MODE = "permissionMode"
        private const val SESSION_ID = "octo.sessionId"

        private const val AUTH_TIMEOUT_MS = 10_000L
        private val BASE_ARGS =
            listOf(
                "-p",
                "--input-format",
                "stream-json",
                "--output-format",
                "stream-json",
                "--verbose",
                "--include-partial-messages",
                "--permission-prompt-tool",
                "stdio",
            )
    }
}

@OptIn(ExperimentalUuidApi::class)
private fun randomSessionId(): String = Uuid.random().toString()
