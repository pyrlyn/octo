package octo.core.triggers

import kotlinx.serialization.Serializable
import octo.core.agent.AgentId
import octo.core.agent.PermissionMode
import octo.core.task.NewTask

/** Starts [task] on a [schedule] or when its [webhook] is called; exactly one of the two. */
@Serializable
data class TriggerConfig(
    val id: String,
    /** Standard five-field cron (`minute hour day month weekday`) in local time. */
    val schedule: String? = null,
    val webhook: WebhookConfig? = null,
    val task: TaskTemplate,
) {
    fun problem(): String? =
        when {
            !ID.matches(id) -> {
                "Trigger id '$id' must be letters, digits, '-' or '_'"
            }

            (schedule == null) == (webhook == null) -> {
                "Trigger '$id' needs exactly one of schedule and webhook"
            }

            webhook != null && webhook.secret.length < MIN_SECRET -> {
                "Trigger '$id' needs a secret of $MIN_SECRET or more characters"
            }

            else -> {
                task.problem()?.let { "Trigger '$id': $it" }
            }
        }

    private companion object {
        val ID = Regex("[A-Za-z0-9_-]+")
        const val MIN_SECRET = 16
    }
}

/**
 * Called as `POST /api/v1/hooks/<id>`. The caller signs the body with [secret] as GitHub does
 * (`X-Hub-Signature-256`) or sends it as `Authorization: Bearer <secret>`.
 */
@Serializable
data class WebhookConfig(
    val secret: String,
    /** `X-GitHub-Event` values to act on; empty takes every event. */
    val events: List<String> = emptyList(),
    /** A dotted path into the JSON body, such as `pull_request.base.ref`, to the value it must have. */
    val filters: Map<String, String> = emptyMap(),
)

/** What a trigger starts, as in the new-task form. */
@Serializable
data class TaskTemplate(
    val repo: String,
    val baseRef: String,
    val agent: String,
    val title: String,
    val prompt: String? = null,
    val options: Map<String, String> = emptyMap(),
    val machine: String? = null,
    val permissionMode: PermissionMode? = null,
) {
    fun problem(): String? =
        listOf("repo" to repo, "baseRef" to baseRef, "agent" to agent, "title" to title)
            .firstOrNull { it.second.isBlank() }
            ?.let { "the task needs a ${it.first}" }

    fun newTask() =
        NewTask(
            repo = repo,
            baseRef = baseRef,
            agent = AgentId(agent),
            title = title,
            prompt = prompt?.takeIf { it.isNotBlank() },
            options = options,
            machine = machine,
            permissionMode = permissionMode,
        )
}
