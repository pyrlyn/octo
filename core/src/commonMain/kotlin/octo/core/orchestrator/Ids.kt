package octo.core.orchestrator

import octo.core.task.TaskId
import kotlin.random.Random

private const val HEX = 16
private const val ID_LENGTH = 8
private const val SLUG_LENGTH = 32
private val NOT_SLUG = Regex("[^a-z0-9]+")

internal fun randomId(): String =
    Random
        .nextInt()
        .toUInt()
        .toString(HEX)
        .padStart(ID_LENGTH, '0')

/** A worktree and branch name readable at a glance, made unique by the task id. */
internal fun slug(
    title: String,
    id: TaskId,
): String {
    val words =
        title
            .lowercase()
            .replace(NOT_SLUG, "-")
            .trim('-')
            .take(SLUG_LENGTH)
            .trimEnd('-')
    return if (words.isEmpty()) id.value else "$words-${id.value}"
}
