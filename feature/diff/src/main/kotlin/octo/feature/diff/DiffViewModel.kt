package octo.feature.diff

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import octo.core.git.FileChange
import octo.core.git.FileDiff
import octo.core.git.TaskDiff
import octo.core.orchestrator.OctoCore
import octo.core.task.TaskId
import octo.feature.common.action
import octo.feature.common.childScope
import octo.feature.common.userMessage

data class DiffUiState(
    val loading: Boolean = true,
    val diff: TaskDiff? = null,
    val selectedPath: String? = null,
    val error: String? = null,
    /** Null when the selected file has no preview; the diff shows instead. */
    val preview: PreviewState? = null,
    /** The user asked for the diff of a file that has a preview. */
    val showDiff: Boolean = false,
) {
    val files: List<FileDiff> get() = diff?.files.orEmpty()
    val selected: FileDiff? get() = files.firstOrNull { it.path == selectedPath }
}

sealed interface PreviewState {
    data object Loading : PreviewState

    data class Ready(
        val preview: Preview,
    ) : PreviewState

    data class Failed(
        val message: String,
    ) : PreviewState
}

/** A task's changes against its base branch, loaded on open and on refresh. */
class DiffViewModel(
    private val core: OctoCore,
    val taskId: TaskId,
    parent: CoroutineScope,
    /** Null while the `previews` flag is off. */
    private val previewer: FilePreviewer? = null,
) {
    private val scope = parent.childScope()
    private val mutable = MutableStateFlow(DiffUiState())
    val state: StateFlow<DiffUiState> = mutable.asStateFlow()
    private var previewing: Job? = null

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
            loadPreview()
        }
    }

    fun select(path: String) {
        mutable.update { it.copy(selectedPath = path, showDiff = false) }
        loadPreview()
    }

    fun toggleDiff() = mutable.update { it.copy(showDiff = !it.showDiff) }

    private fun loadPreview() {
        previewing?.cancel()
        val path =
            mutable.value.selected
                ?.takeIf { it.change != FileChange.Deleted }
                ?.path
        // Only this computer's worktrees can be read; a remote task's files stay on its machine.
        val worktree =
            core.state.value[taskId]
                ?.task
                ?.takeIf { it.machine == null }
                ?.worktree
        val previewer = previewer?.takeIf { path != null && it.canPreview(path) }
        if (path == null || worktree == null || previewer == null) {
            mutable.update { it.copy(preview = null) }
            return
        }
        mutable.update { it.copy(preview = PreviewState.Loading) }
        previewing =
            scope.action({ e -> show(path, PreviewState.Failed(e.userMessage)) }) {
                show(path, PreviewState.Ready(previewer.preview(worktree, path)))
            }
    }

    private fun show(
        path: String,
        preview: PreviewState,
    ) = mutable.update { if (it.selectedPath == path) it.copy(preview = preview) else it }

    fun close() = scope.cancel()
}
