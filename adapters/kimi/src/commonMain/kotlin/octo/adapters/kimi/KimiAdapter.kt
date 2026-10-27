package octo.adapters.kimi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import octo.adapters.base.AcpAgentAdapter
import octo.core.agent.AgentId
import octo.core.agent.SessionSpec
import octo.core.agent.WrongAgentBinaryException
import octo.core.process.ProcessLauncher
import octo.core.process.ProcessSpec
import octo.core.process.run
import kotlin.coroutines.CoroutineContext

/** Moonshot's Kimi Code CLI over its native ACP server, `kimi acp`. */
class KimiAdapter(
    launcher: ProcessLauncher,
    context: CoroutineContext = Dispatchers.Default,
) : AcpAgentAdapter(launcher, context) {
    override val id = ID
    override val displayName = "Kimi Code"
    override val binary = "kimi"
    override val verifiedVersion = "0.39.1"

    // `login` only re-checks the token `kimi login` stored; it never starts a sign-in.
    override val loginMethods = listOf("login")

    // Kimi Code prints a bare version; the archived Python kimi-cli installs a `kimi` binary too and
    // prints `kimi, version 1.x`, so it reads as unknown.
    override fun parseVersion(output: String): String? = KIMI_CODE_VERSION.find(output)?.groupValues?.get(1)

    override suspend fun checkBinary(binaryPath: String) {
        val output =
            withTimeoutOrNull(VERSION_TIMEOUT_MS) {
                launcher.run(ProcessSpec(binaryPath, versionArgs)).stdout
            }
        if (output == null || parseVersion(output) == null) {
            throw WrongAgentBinaryException(id, binaryPath, "expected Kimi Code CLI, not the legacy Python kimi-cli")
        }
    }

    override fun buildArgs(spec: SessionSpec): List<String> = listOf("acp")

    companion object {
        val ID = AgentId("kimi")
        private val KIMI_CODE_VERSION = Regex("""^(\d+\.\d+\.\d+\S*)$""", RegexOption.MULTILINE)
        private const val VERSION_TIMEOUT_MS = 10_000L
    }
}
