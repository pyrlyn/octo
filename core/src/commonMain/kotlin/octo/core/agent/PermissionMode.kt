package octo.core.agent

import kotlinx.serialization.Serializable

/**
 * How much an agent may do without asking, in one vocabulary for every agent; each adapter maps
 * it to its own setting and lists the modes it has in [AgentAdapter.permissionModes].
 */
@Serializable
enum class PermissionMode(
    val label: String,
) {
    /** Asks before commands and file changes. */
    Supervised("Supervised"),

    /** Applies file edits without asking; other actions may still ask. */
    AutoAcceptEdits("Auto-accept edits"),

    /** The agent's own automatic review approves routine actions and asks about the rest. */
    Auto("Auto"),

    /** Runs commands and edits without asking. */
    FullAccess("Full access"),
    ;

    /** This mode when [supported] has it; otherwise Supervised, which every agent has. */
    fun within(supported: Set<PermissionMode>): PermissionMode = if (this in supported) this else Supervised

    /** The less permissive of this and [limit]; the modes are declared from least to most permissive. */
    fun atMost(limit: PermissionMode): PermissionMode = minOf(this, limit)
}
