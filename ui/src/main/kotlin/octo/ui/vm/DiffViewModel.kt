package octo.ui.vm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import octo.core.git.FileDiff
import octo.core.git.TaskDiff
import octo.core.orchestrator.OctoCore
import octo.core.task.TaskId

data class DiffUiState(
    val loading: Boolean = true,
    val diff: TaskDiff? = null,
    val selectedPath: String? = null,
    val error: String? = null,
) {
    val files: List<FileDiff> get() = diff?.files.orEmpty()
    val selected: FileDiff? get() = files.firstOrNull { it.path == selectedPath }
}

/** A task's changes against its base branch, loaded on open and on refresh. */
class DiffViewModel(
    private val core: OctoCore,
    val taskId: TaskId,
    parent: CoroutineScope,
) {
    private val scope = parent.childScope()
    private val mutable = MutableStateFlow(DiffUiState())
    val state: StateFlow<DiffUiState> = mutable.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        mutable.update { it.copy(loading = true, error = null) }
        scope.action({ e -> mutable.update { it.copy(loading = false, error = e.userMessage) } }) {
            val diff = core.diff(taskId)
            mutable.update { s ->
                // Keep the open file across refreshes while it still has changes.
                val path =
                    s.selectedPath?.takeIf { p -> diff.files.any { it.path == p } } ?: diff.files.firstOrNull()?.path
                s.copy(loading = false, diff = diff, selectedPath = path)
            }
        }
    }

    fun select(path: String) = mutable.update { it.copy(selectedPath = path) }

    fun close() = scope.cancel()
}
