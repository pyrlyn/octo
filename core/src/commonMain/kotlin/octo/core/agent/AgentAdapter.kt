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

    suspend fun probe(): AgentProbe

    suspend fun start(spec: SessionSpec): AgentSession
}

interface AgentSession {
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
