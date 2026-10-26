package octo.adapters.antigravity

import kotlinx.coroutines.Dispatchers
import octo.adapters.base.AcpAgentAdapter
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
