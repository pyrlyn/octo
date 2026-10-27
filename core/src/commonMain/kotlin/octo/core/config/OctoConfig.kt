package octo.core.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import octo.core.agent.PermissionMode
import octo.core.flags.FeatureFlags
import octo.core.remote.SshProcessLauncher

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
) {
    fun featureFlags(): FeatureFlags = FeatureFlags(flags)

    companion object {
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
) {
    /** Why this entry cannot be used, or null; checked before any value reaches `ssh`. */
    fun problem(): String? =
        when {
            !id.matches(ID) -> "Machine id '$id' must be lowercase letters, digits and dashes"
            !SshProcessLauncher.isValidHost(host) -> "Machine '$id' has an invalid host '$host'"
            port != null && !SshProcessLauncher.isValidPort(port) -> "Machine '$id' has an invalid port $port"
            worktrees != null && !worktrees.startsWith("/") -> "Machine '$id' needs an absolute worktrees path"
            else -> null
        }

    private companion object {
        val ID = Regex("[a-z0-9][a-z0-9-]*")
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

data class ParsedConfig(
    val config: OctoConfig,
    val warning: String?,
)
