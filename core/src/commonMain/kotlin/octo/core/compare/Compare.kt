package octo.core.compare

import octo.core.agent.AgentId
import octo.core.agent.MODEL_OPTION
import octo.core.orchestrator.OctoCore
import octo.core.task.NewTask
import octo.core.task.TaskGroup
import octo.core.task.TaskId
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

/** One agent, on its default model when [model] is null. */
data class AgentPick(
    val agent: AgentId,
    val model: String? = null,
) {
    val label: String get() = agent.value + model?.let { " ($it)" }.orEmpty()
}

data class Comparison(
    val tasks: List<TaskId>,
    /** Each pick that did not start, with why. */
    val failures: List<Pair<AgentPick, String>>,
)

/**
 * Starts one task per pick from [request], each on its own branch with the same prompt, all in
 * one group named after the request's title. A pick that fails does not stop the others, so one
 * missing agent never costs the rest of the comparison. Whatever one pick throws is that pick's
 * failure only.
 */
@Suppress("TooGenericExceptionCaught")
suspend fun OctoCore.compare(
    request: NewTask,
    picks: List<AgentPick>,
    groupId: String = Random.nextLong().toULong().toString(GROUP_ID_RADIX),
): Comparison {
    val group = TaskGroup(groupId, request.title)
    val tasks = mutableListOf<TaskId>()
    val failures = mutableListOf<Pair<AgentPick, String>>()
    for (pick in picks.distinct()) {
        try {
            tasks +=
                createTask(
                    request.copy(
                        agent = pick.agent,
                        title = "${request.title} · ${pick.label}",
                        options =
                            request.options - MODEL_OPTION +
                                listOfNotNull(
                                    pick.model?.let { MODEL_OPTION to it },
                                ),
                        group = group,
                    ),
                )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failures += pick to (e.message ?: "It did not start")
        }
    }
    return Comparison(tasks, failures)
}

private const val GROUP_ID_RADIX = 36
