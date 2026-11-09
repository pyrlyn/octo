package octo.adapters.antigravity

import kotlinx.coroutines.Dispatchers
import octo.adapters.base.AcpAgentAdapter
import octo.core.agent.AgentIcon
import octo.core.agent.AgentId
import octo.core.agent.SessionSpec
import octo.core.agent.VendorTerms
import octo.core.flags.FeatureFlag
import octo.core.paths.HostOs
import octo.core.process.ProcessLauncher
import kotlin.coroutines.CoroutineContext

/**
 * Google Antigravity through its ACP server. The binary and arguments follow the ACP registry
 * entry `antigravity-acp` 1.4.3; the server itself has no published protocol documentation.
 */
class AntigravityAdapter(
    launcher: ProcessLauncher,
    private val os: HostOs,
    context: CoroutineContext = Dispatchers.Default,
) : AcpAgentAdapter(launcher, context) {
    override val id = ID
    override val displayName = "Antigravity"
    override val icon =
        AgentIcon(
            """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><defs><linearGradient id="g" x1="3.5" y1="8" x2="20.5" y2="16" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#F0552E"/><stop offset=".3" stop-color="#F6B21B"/><stop offset=".65" stop-color="#2FB36B"/><stop offset=".9" stop-color="#3B7BF6"/></linearGradient></defs><rect width="24" height="24" rx="6" fill="#1E1A3A"/><path d="M12 3.5C12.9 8.1 15.9 11.1 20.5 12C15.9 12.9 12.9 15.9 12 20.5C11.1 15.9 8.1 12.9 3.5 12C8.1 11.1 11.1 8.1 12 3.5Z" fill="url(#g)"/></svg>""",
        )
    override val featureFlag = FLAG
    override val terms = TERMS
    override val binary = "agy_acp_server.par"
    override val verifiedVersion = "1.4.3"

    // The archive ships `agy_acp_server.par` on macOS and Linux and `agy_acp_server.exe` on Windows.
    override suspend fun locate(): String? = launcher.which(binary) ?: launcher.which("agy_acp_server")

    // The registry passes an empty `--uid=` on Linux only.
    override fun buildArgs(spec: SessionSpec): List<String> = if (os == HostOs.Linux) listOf("--uid=") else emptyList()

    companion object {
        val ID = AgentId("antigravity")

        val FLAG = FeatureFlag("antigravity", "Run Google Antigravity through its ACP server")

        val TERMS =
            VendorTerms(
                version = "2026-10-26",
                summary =
                    "Google's Antigravity FAQ says that wrapping the agent in third-party applications is not " +
                        "supported on consumer plans and calls such third-party access a Terms of Service violation. " +
                        "Octo runs your own installed Antigravity under your own login. Check that your plan " +
                        "allows this before you continue.",
                url = "https://antigravity.google/terms",
            )
    }
}
