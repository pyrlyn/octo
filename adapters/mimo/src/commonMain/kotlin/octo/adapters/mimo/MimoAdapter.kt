package octo.adapters.mimo

import kotlinx.coroutines.Dispatchers
import octo.adapters.base.AcpAgentAdapter
import octo.core.agent.AgentIcon
import octo.core.agent.AgentId
import octo.core.agent.SessionSpec
import octo.core.process.ProcessLauncher
import kotlin.coroutines.CoroutineContext

/** Xiaomi MiMo Code over its native ACP server, `mimo acp`. */
class MimoAdapter(
    launcher: ProcessLauncher,
    context: CoroutineContext = Dispatchers.Default,
) : AcpAgentAdapter(launcher, context) {
    override val id = ID
    override val displayName = "MiMo Code"
    override val icon =
        AgentIcon(
            """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><rect width="24" height="24" rx="6" fill="#FF6900"/><path d="M6 17V7h3l3 5 3-5h3v10" fill="none" stroke="#fff" stroke-width="2.2" stroke-linejoin="round"/></svg>""",
        )
    override val binary = "mimo"
    override val verifiedVersion = "0.1.16"

    // No login methods: MiMo's only one, `opencode-login`, is an interactive sign-in and its
    // `authenticate` is not implemented, so a missing login surfaces as the agent's own error.

    override fun buildArgs(spec: SessionSpec): List<String> = listOf("acp")

    companion object {
        val ID = AgentId("mimo")
    }
}
