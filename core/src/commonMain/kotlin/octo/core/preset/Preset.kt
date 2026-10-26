package octo.core.preset

import kotlinx.serialization.Serializable
import octo.core.agent.AgentId
import octo.core.orchestrator.OctoCore
import octo.core.task.NewTask
import octo.core.task.TaskId

/** A saved team: one agent per member, started together on one repository. */
@Serializable
data class Preset(
    val name: String,
    val members: List<PresetMember>,
) {
    /** Why this preset cannot be saved, or null when it can. */
    fun problem(): String? =
        when {
            name.isBlank() -> "A preset needs a name"
            members.isEmpty() -> "A preset needs at least one member"
            members.any { it.role.isBlank() } -> "Every member needs a role"
            members.any { it.agent.value.isBlank() } -> "Every member needs an agent"
            members.map { it.role.trim() }.toSet().size != members.size -> "Each role can appear only once"
            else -> null
        }
}

@Serializable
data class PresetMember(
    val role: String,
    val agent: AgentId,
    val prompt: String = "",
)

/**
 * Starts one task per member, in order. A member that fails to start stops the rest; the tasks
 * already started stay, as if the user had created them one by one.
 */
suspend fun OctoCore.startPreset(
    preset: Preset,
    repo: String,
    baseRef: String,
    machine: String? = null,
): List<TaskId> =
    preset.members.map { member ->
        createTask(
            NewTask(
                repo = repo,
                baseRef = baseRef,
                agent = member.agent,
                title = "${preset.name.trim()}: ${member.role.trim()}",
                prompt = firstPrompt(preset, member),
                machine = machine,
            ),
        )
    }

// A lone agent needs no team note; in a team each agent should know who else is working.
internal fun firstPrompt(
    preset: Preset,
    member: PresetMember,
): String? {
    val own = member.prompt.trim()
    if (preset.members.size == 1) return own.ifEmpty { null }
    val others = preset.members.filter { it !== member }.joinToString { "${it.role.trim()} (${it.agent.value})" }
    val note =
        "You are the ${member.role.trim()} in the Octo team \"${preset.name.trim()}\". " +
            "Your teammates, each in its own task and worktree: $others."
    return if (own.isEmpty()) note else "$note\n\n$own"
}
