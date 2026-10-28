package octo.core.orchestrator

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import octo.core.task.Attachment
import octo.core.task.OctoState
import octo.core.task.QueuedPrompt
import octo.core.task.TaskId

/** The messages each task holds until its agent is free; the caller holds the task's lock. */
internal class PromptQueues(
    private val state: MutableStateFlow<OctoState>,
    private val limit: Int,
) {
    fun add(
        id: TaskId,
        text: String,
        attachments: List<Attachment> = emptyList(),
    ) {
        val queue = queue(id)
        check(queue.size < limit) { "Task '${id.value}' already has $limit messages waiting" }
        val next = QueuedPrompt((queue.maxOfOrNull { it.id } ?: 0) + 1, text, attachments)
        change(id) { it + next }
    }

    fun edit(
        id: TaskId,
        queuedId: Int,
        text: String,
    ) {
        check(queue(id).any { it.id == queuedId }) { "That message was already sent" }
        change(id) { queue -> queue.map { if (it.id == queuedId) it.copy(text = text) else it } }
    }

    /** Removes the prompt; returns it, or null when it was already sent. */
    fun remove(
        id: TaskId,
        queuedId: Int,
    ): QueuedPrompt? = queue(id).firstOrNull { it.id == queuedId }?.also { change(id) { queue -> queue - it } }

    /** Puts [prompt] back at the head, for one that could not be sent. */
    fun putBack(
        id: TaskId,
        prompt: QueuedPrompt,
    ) = change(id) { listOf(prompt) + it }

    private fun queue(id: TaskId) =
        state.value[id]
            ?.task
            ?.queue
            .orEmpty()

    private fun change(
        id: TaskId,
        change: (List<QueuedPrompt>) -> List<QueuedPrompt>,
    ) = state.update { s -> s.change(id) { it.copy(task = it.task.copy(queue = change(it.task.queue))) } }
}
