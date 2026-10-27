package octo.adapters.grok

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import octo.adapters.base.AcpAgentAdapter
import octo.core.agent.AgentIcon
import octo.core.agent.AgentId
import octo.core.agent.SessionSpec
import octo.core.agent.WrongAgentBinaryException
import octo.core.process.ProcessLauncher
import octo.core.process.ProcessSpec
import octo.core.process.run
import kotlin.coroutines.CoroutineContext

/** xAI Grok Build over its native ACP server, `grok agent stdio`. */
class GrokAdapter(
    launcher: ProcessLauncher,
    context: CoroutineContext = Dispatchers.Default,
) : AcpAgentAdapter(launcher, context) {
    override val id = ID
    override val displayName = "Grok"
    override val icon =
        AgentIcon(
            """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><rect width="24" height="24" rx="6" fill="#111827"/><path d="M8 18L16 6" stroke="#fff" stroke-width="2.6" stroke-linecap="round"/></svg>""",
        )
    override val binary = "grok"
    override val verifiedVersion = "1.0.67"

    // Grok Build reads its own cached login; the `grok.com` method is an interactive sign-in.
    override val loginMethods = listOf("cached_token")

    // Only Grok Build prints `grok <version> (<commit>)`; the community `grok-dev` package installs
    // a `grok` binary too and prints a bare version, so it reads as unknown.
    override fun parseVersion(output: String): String? = OFFICIAL_VERSION.find(output)?.groupValues?.get(1)

    override suspend fun checkBinary(binaryPath: String) {
        val output =
            withTimeoutOrNull(VERSION_TIMEOUT_MS) {
                launcher.run(ProcessSpec(binaryPath, versionArgs)).stdout
            }
        if (output == null || parseVersion(output) == null) {
            throw WrongAgentBinaryException(id, binaryPath, "expected xAI Grok Build, not the community grok-cli")
        }
    }

    override fun buildArgs(spec: SessionSpec): List<String> = listOf("agent", "stdio")

    companion object {
        val ID = AgentId("grok")
        private val OFFICIAL_VERSION = Regex("""^grok (\d+\.\d+\.\d+)\S* \(""", RegexOption.MULTILINE)
        private const val VERSION_TIMEOUT_MS = 10_000L
    }
}
