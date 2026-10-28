package octo.adapters.zcode

import kotlinx.coroutines.Dispatchers
import octo.adapters.base.CliAgentAdapter
import octo.core.agent.AgentIcon
import octo.core.agent.AgentId
import octo.core.agent.AgentSession
import octo.core.agent.Capability
import octo.core.agent.PermissionMode
import octo.core.agent.SessionSpec
import octo.core.process.ProcessLauncher
import kotlin.coroutines.CoroutineContext

/**
 * Z.ai's ZCode CLI through its documented headless mode, one `zcode --prompt` run per turn with
 * `--output-format stream-json`. Its `app-server` is the desktop app's internal protocol, so Octo
 * does not use it.
 */
class ZCodeAdapter(
    launcher: ProcessLauncher,
    context: CoroutineContext = Dispatchers.Default,
) : CliAgentAdapter(launcher, context) {
    override val id = ID
    override val displayName = "ZCode"
    override val icon =
        AgentIcon(
            """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><rect width="24" height="24" rx="6" fill="#0F172A"/><path d="M7 7h10L7 17h10" fill="none" stroke="#fff" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/></svg>""",
        )
    override val capabilities = setOf(Capability.Resume, Capability.Cancel, Capability.Images)

    // Each turn is a new run, so a mode change applies from the next prompt.
    override val permissionModes = PermissionMode.entries.toSet()
    override val binary = "zcode"
    override val verifiedVersion = "3.16.2"

    override suspend fun start(spec: SessionSpec): AgentSession = ZCodeSession(launcher, binaryPath(), spec, context)

    companion object {
        val ID = AgentId("zcode")
    }
}
