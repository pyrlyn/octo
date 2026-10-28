package octo.core.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import octo.core.agent.PermissionMode
import octo.core.flags.FeatureFlags
import octo.core.remote.SshProcessLauncher
import octo.core.triggers.TriggerConfig

/** User settings from `config.json` in the Octo home. */
@Serializable
data class OctoConfig(
    val flags: Map<String, Boolean> = emptyMap(),
    /** Remote machines; used only with the `remote-ssh` flag on. */
    val machines: List<MachineConfig> = emptyList(),
    /** Used only with the `remote-access` flag on. */
    val remoteAccess: RemoteAccessConfig = RemoteAccessConfig(),
    /** For new tasks that do not choose one. */
    val defaultPermissionMode: PermissionMode = PermissionMode.Supervised,
    /**
     * A repository's top-level path to the shell command that prepares each new worktree of it
     * (installing dependencies, copying `.env` files) before the agent starts.
     */
    val worktreeSetup: Map<String, String> = emptyMap(),
    /** Used only with the `triggers` flag on; webhooks also need `remote-access`. */
    val triggers: List<TriggerConfig> = emptyList(),
    /** Live agents allowed on this computer; used only with the `queue` flag on. */
    val maxRunningAgents: Int? = null,
    /**
     * Tokens in the context from which an idle conversation is compacted before its prompt cache
     * expires; used only with the `compact-idle` flag on. 0 compacts every idle conversation.
     */
    val compactIdleMinTokens: Long = DEFAULT_COMPACT_IDLE_MIN_TOKENS,
) {
    fun featureFlags(): FeatureFlags = FeatureFlags(flags)

    /** Every `maxRunningAgents` that is set, keyed by machine id (null for this computer). */
    fun agentLimits(): AgentLimits {
        val given =
            (listOf(null to maxRunningAgents) + machines.map { it.id to it.maxRunningAgents })
                .mapNotNull { (machine, limit) -> limit?.let { machine to it } }
        val (valid, invalid) = given.partition { it.second >= 1 }
        return AgentLimits(
            valid.toMap(),
            invalid.map { (machine, limit) ->
                val whose = machine?.let { "machine '$it'" } ?: "this computer"
                "Ignoring maxRunningAgents $limit for $whose: it must be 1 or more"
            },
        )
    }

    companion object {
        /** T3 Code's threshold: with a 200K window Claude compacts on its own before it gets there. */
        const val DEFAULT_COMPACT_IDLE_MIN_TOKENS = 200_000L

        private val json = Json { ignoreUnknownKeys = true }

        /** A broken file must not stop Octo from starting, so it falls back to defaults and says why. */
        fun parse(text: String?): ParsedConfig =
            if (text.isNullOrBlank()) {
                ParsedConfig(OctoConfig(), warning = null)
            } else {
                try {
                    ParsedConfig(json.decodeFromString(serializer(), text), warning = null)
                } catch (e: SerializationException) {
                    ParsedConfig(OctoConfig(), warning = "Ignoring invalid config.json: ${e.message}")
                } catch (e: IllegalArgumentException) {
                    ParsedConfig(OctoConfig(), warning = "Ignoring invalid config.json: ${e.message}")
                }
            }
    }
}

/** A machine reached with the user's own `ssh`; [host] may be an alias from `~/.ssh/config`. */
@Serializable
data class MachineConfig(
    val id: String,
    val host: String,
    val port: Int? = null,
    /** Absolute path on the machine; `$HOME/.octo/worktrees` there when left out. */
    val worktrees: String? = null,
    /** Live agents allowed on the machine; used only with the `queue` flag on. */
    val maxRunningAgents: Int? = null,
    /**
     * A repository's top-level path on this computer to its clone on the machine; used only with
     * the `move-task` flag on.
     */
    val repos: Map<String, String> = emptyMap(),
) {
    /** Why this entry cannot be used, or null; checked before any value reaches `ssh`. */
    fun problem(): String? =
        when {
            !id.matches(ID) -> "Machine id '$id' must be lowercase letters, digits and dashes"
            !SshProcessLauncher.isValidHost(host) -> "Machine '$id' has an invalid host '$host'"
            port != null && !SshProcessLauncher.isValidPort(port) -> "Machine '$id' has an invalid port $port"
            worktrees != null && !worktrees.startsWith("/") -> "Machine '$id' needs an absolute worktrees path"
            repos.any(::badRepo) -> "Machine '$id' needs an absolute clone path for every repository in repos"
            else -> null
        }

    private companion object {
        val ID = Regex("[a-z0-9][a-z0-9-]*")

        fun badRepo(entry: Map.Entry<String, String>) = entry.key.isBlank() || !entry.value.startsWith("/")
    }
}

@Serializable
data class RemoteAccessConfig(
    /** The loopback port; a fixed one, so the user's tunnel keeps pointing at it. */
    val port: Int = DEFAULT_PORT,
) {
    fun problem(): String? = if (port in 1..MAX_PORT) null else "remoteAccess.port $port is not a TCP port"

    companion object {
        const val DEFAULT_PORT = 47470
        private const val MAX_PORT = 65535
    }
}

data class AgentLimits(
    val limits: Map<String?, Int> = emptyMap(),
    val warnings: List<String> = emptyList(),
)

data class ParsedConfig(
    val config: OctoConfig,
    val warning: String?,
)
