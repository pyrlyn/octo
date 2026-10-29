package octo.feature.task

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import octo.core.publish.PublishRequest
import octo.core.publish.PublishResult
import octo.core.publish.TaskPublisher
import octo.core.task.TaskId
import octo.feature.common.action
import octo.feature.common.childScope
import octo.feature.common.userMessage

data class PublishUiState(
    val commitMessage: String = "",
    val title: String = "",
    val body: String = "",
    val running: Boolean = false,
    val result: PublishResult? = null,
    val error: String? = null,
) {
    val canPublish: Boolean get() = !running && title.isNotBlank()
}

/** The commit, push and pull request form for one task. */
class PublishViewModel(
    private val publisher: TaskPublisher,
    val taskId: TaskId,
    taskTitle: String,
    parent: CoroutineScope,
) {
    private val scope = parent.childScope()
    private val mutable = MutableStateFlow(PublishUiState(commitMessage = taskTitle, title = taskTitle))
    val state: StateFlow<PublishUiState> = mutable.asStateFlow()

    fun editCommitMessage(value: String) = mutable.update { it.copy(commitMessage = value) }

    fun editTitle(value: String) = mutable.update { it.copy(title = value) }

    fun editBody(value: String) = mutable.update { it.copy(body = value) }

    fun publish() {
        val s = state.value.takeIf { it.canPublish } ?: return
        mutable.update { it.copy(running = true, error = null) }
        scope.action({ e -> mutable.update { it.copy(running = false, error = e.userMessage) } }) {
            val result =
                publisher.publish(
                    taskId,
                    PublishRequest(s.commitMessage.trim(), s.title.trim(), s.body.trim()),
                )
            mutable.update { it.copy(running = false, result = result) }
        }
    }

    fun close() = scope.cancel()
}
