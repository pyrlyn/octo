package octo.core.agent

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import octo.core.flags.FeatureFlag
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class AgentId(
    val value: String,
)

/** The agent's own session id, kept so a restarted task can resume the same conversation. */
@Serializable
@JvmInline
value class AgentSessionRef(
    val value: String,
)

enum class Capability {
    Resume,
    Cancel,
    Permissions,
    Questions,
    Plan,
    ModelSelection,

    /** Takes the MCP servers Octo offers in [SessionSpec.mcpServers], such as agent messaging. */
    Mcp,
}

enum class AuthState { LoggedIn, LoggedOut, Unknown }

data class AgentProbe(
    val binaryPath: String?,
    val version: String?,
    val verifiedVersion: String,
    val auth: AuthState,
) {
    val installed: Boolean get() = binaryPath != null

    /** The adapter's parser was checked against [verifiedVersion]; other versions may change the protocol. */
    val versionDrift: Boolean get() = version != null && version != verifiedVersion
}

/** An agent's glyph as SVG markup, shipped with its adapter so the UI needs no per-agent code. */
@JvmInline
value class AgentIcon(
    val svg: String,
)

/** A change of [version] shows the notice again. */
data class VendorTerms(
    val version: String,
    val summary: String,
    val url: String,
)

data class SessionSpec(
    val workingDir: String,
    val resume: AgentSessionRef? = null,
    val env: Map<String, String> = emptyMap(),
    val options: Map<String, String> = emptyMap(),
    /** Octo's own servers; only adapters with [Capability.Mcp] get any. */
    val mcpServers: List<McpServer> = emptyList(),
)

/** An MCP server Octo runs for the agent over streamable HTTP; [headers] carry its per-task token. */
data class McpServer(
    val name: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
)

/**
 * One agent integration. The core never branches on [id]; behaviour differences are expressed
 * through [capabilities] and the [AgentEvent]s a session emits.
 */
interface AgentAdapter {
    val id: AgentId
    val displayName: String
    val capabilities: Set<Capability>

    /** When set, the adapter is registered only while this flag is enabled. */
    val featureFlag: FeatureFlag? get() = null

    /** Vendor terms the user must acknowledge before the first session (see `TermsGate`). */
    val terms: VendorTerms? get() = null

    /** Null shows a monogram of [displayName]. */
    val icon: AgentIcon? get() = null

    suspend fun probe(): AgentProbe

    suspend fun start(spec: SessionSpec): AgentSession
}

interface AgentSession {
    /** The agent's root process on the machine that launched it, for CPU and memory figures. */
    val processId: Long? get() = null

    /** Normalized events; single collector. Completes after [AgentEvent.Exited]. */
    val events: Flow<AgentEvent>

    suspend fun prompt(text: String)

    suspend fun respond(
        requestId: String,
        response: RequestResponse,
    )

    /** Cancels the current turn; the session stays usable. */
    suspend fun cancel()

    /** Ends the agent process. */
    suspend fun close()
}

class AgentNotInstalledException(
    id: AgentId,
) : IllegalStateException("Agent '${id.value}' is not installed or not on PATH")

/** The binary on PATH has the agent's name but is a different program. */
class WrongAgentBinaryException(
    id: AgentId,
    path: String,
    reason: String,
) : IllegalStateException("'$path' is not the ${id.value} agent Octo supports: $reason")
