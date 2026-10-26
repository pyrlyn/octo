package octo.core.plugins

import kotlinx.serialization.Serializable

/** What a plugin says it adds, as `octo_manifest` returns it. Every field comes from the plugin. */
@Serializable
data class PluginManifest(
    val abi: Int,
    val id: String,
    val name: String,
    val version: String,
    val agents: List<PluginAgent> = emptyList(),
    val actions: List<PluginAction> = emptyList(),
) {
    /** Why Octo cannot use this manifest, or null. */
    fun problem(): String? =
        when {
            !id.matches(ID) -> "id '$id' must be lowercase letters, digits and dashes"
            !name.isLabel() -> "name must be 1 to $MAX_LABEL printable characters"
            !version.isLabel() -> "version must be 1 to $MAX_LABEL printable characters"
            agents.size > MAX_ITEMS || actions.size > MAX_ITEMS -> "more than $MAX_ITEMS agents or actions"
            agents.distinctBy { it.id }.size != agents.size -> "two agents share an id"
            actions.distinctBy { it.id }.size != actions.size -> "two actions share an id"
            else -> agents.firstNotNullOfOrNull { it.problem() } ?: actions.firstNotNullOfOrNull { it.problem() }
        }

    companion object {
        /** The plugin ABI this Octo speaks; see `docs/plugins.md`. */
        const val ABI = 1
        internal const val MAX_LABEL = 60
        private const val MAX_ITEMS = 32
        internal val ID = Regex("[a-z0-9][a-z0-9-]{0,39}")
    }
}

/**
 * An ACP agent the plugin knows how to start. [command] is a program name looked up on PATH,
 * never a path or a shell line, so a plugin cannot point Octo at a file of its choosing.
 */
@Serializable
data class PluginAgent(
    val id: String,
    val displayName: String,
    val command: String,
    val args: List<String> = emptyList(),
    val verifiedVersion: String,
) {
    fun problem(): String? =
        when {
            !id.matches(PluginManifest.ID) -> {
                "agent id '$id' must be lowercase letters, digits and dashes"
            }

            !displayName.isLabel() -> {
                "agent '$id' needs a display name of 1 to ${PluginManifest.MAX_LABEL} characters"
            }

            !command.matches(COMMAND) -> {
                "agent '$id' command '$command' must be a bare program name"
            }

            args.size > MAX_ARGS || args.any { it.length > MAX_ARG || it.any(Char::isISOControl) } -> {
                "agent '$id' has more than $MAX_ARGS arguments or one that is too long"
            }

            !verifiedVersion.isLabel() -> {
                "agent '$id' needs a verified version"
            }

            else -> {
                null
            }
        }

    private companion object {
        val COMMAND = Regex("[A-Za-z0-9_][A-Za-z0-9._-]{0,63}")
        const val MAX_ARGS = 32
        const val MAX_ARG = 200
    }
}

/** Something the user can run on a task from the task screen. */
@Serializable
data class PluginAction(
    val id: String,
    val title: String,
) {
    fun problem(): String? =
        when {
            !id.matches(PluginManifest.ID) -> "action id '$id' must be lowercase letters, digits and dashes"
            !title.isLabel() -> "action '$id' needs a title of 1 to ${PluginManifest.MAX_LABEL} characters"
            else -> null
        }
}

private fun String.isLabel() = isNotBlank() && length <= PluginManifest.MAX_LABEL && none { it.isISOControl() }
