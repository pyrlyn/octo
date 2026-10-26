package octo.adapters.base

import kotlinx.coroutines.Dispatchers
import octo.core.agent.Capability
import octo.core.agent.SessionSpec
import octo.core.process.ProcessLauncher
import octo.core.process.RunningProcess
import kotlin.coroutines.CoroutineContext

/** An agent with native ACP over stdio. Subclasses name the binary and its ACP arguments. */
abstract class AcpAgentAdapter(
    launcher: ProcessLauncher,
    context: CoroutineContext = Dispatchers.Default,
    protected val clientVersion: String = "0.1.0",
) : BaseAgentAdapter(launcher, context) {
    override val capabilities: Set<Capability> =
        setOf(Capability.Resume, Capability.Cancel, Capability.Permissions, Capability.Plan, Capability.Mcp)

    /** `authenticate` method ids that reuse the CLI's own login; never ones that take a key. */
    protected open val loginMethods: List<String> = emptyList()

    protected val acpConfig: AcpConfig get() = AcpConfig(clientVersion, loginMethods)

    override fun createSession(
        process: RunningProcess,
        spec: SessionSpec,
    ): ProcessAgentSession = AcpSession(process, context, acpConfig)
}
