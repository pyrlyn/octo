package octo.ui.vm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import octo.core.agent.AgentId
import octo.core.orchestrator.OctoCore
import octo.core.task.OctoState
import octo.core.task.SidebarStatus
import octo.core.task.TaskId
import octo.core.task.rollup

data class TaskRow(
    val id: TaskId,
    val title: String,
    val agent: AgentId,
    val branch: String,
    val status: SidebarStatus,
)

data class RepoGroup(
    val repo: String,
    val name: String,
    val status: SidebarStatus?,
    val tasks: List<TaskRow>,
)

data class MainUiState(
    val groups: List<RepoGroup> = emptyList(),
    val selected: TaskId? = null,
    val sidebarWidth: Float = DEFAULT_SIDEBAR_WIDTH,
    val diffOpen: Boolean = false,
    val newTaskOpen: Boolean = false,
    val warnings: List<String> = emptyList(),
    val error: String? = null,
    val warningsDismissed: Boolean = false,
) {
    companion object {
        const val DEFAULT_SIDEBAR_WIDTH = 280f
        const val MIN_SIDEBAR_WIDTH = 180f
        const val MAX_SIDEBAR_WIDTH = 520f
    }
}

/** The window: the task list grouped by repository, the selection and the persisted layout. */
class MainViewModel(
    private val core: OctoCore,
    private val scope: CoroutineScope,
    /** Problems found before core started, shown with the ones core reports. */
    private val startupWarnings: List<String> = emptyList(),
) {
    private val mutable = MutableStateFlow(MainUiState().withCore(core.state.value, startupWarnings))
    val state: StateFlow<MainUiState> = mutable.asStateFlow()

    init {
        act { core.state.collect { octo -> mutable.update { it.withCore(octo, startupWarnings) } } }
    }

    fun select(id: TaskId) {
        act {
            core.updateLayout(
                core.state.value.layout
                    .copy(selectedTask = id),
            )
            core.markSeen(id)
        }
    }

    fun resizeSidebar(width: Float) {
        val clamped = width.coerceIn(MainUiState.MIN_SIDEBAR_WIDTH, MainUiState.MAX_SIDEBAR_WIDTH)
        act {
            core.updateLayout(
                core.state.value.layout
                    .copy(sidebarWidth = clamped),
            )
        }
    }

    fun showDiff(open: Boolean) {
        act {
            core.updateLayout(
                core.state.value.layout
                    .copy(diffOpen = open),
            )
        }
    }

    fun openNewTask() = mutable.update { it.copy(newTaskOpen = true) }

    fun closeNewTask() = mutable.update { it.copy(newTaskOpen = false) }

    /** Called once a task was created from the dialog. */
    fun created(id: TaskId) {
        closeNewTask()
        select(id)
    }

    fun dismissWarnings() = mutable.update { it.copy(warningsDismissed = true, warnings = emptyList()) }

    fun dismissError() = mutable.update { it.copy(error = null) }

    private fun act(block: suspend () -> Unit) =
        scope.action({ e ->
            mutable.update {
                it.copy(error = e.userMessage)
            }
        }, block)
}

private fun MainUiState.withCore(
    state: OctoState,
    startupWarnings: List<String>,
): MainUiState {
    val groups =
        state.tasks
            .groupBy { it.task.repo }
            .map { (repo, views) ->
                RepoGroup(
                    repo = repo,
                    name = repo.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\'),
                    status = views.rollup(),
                    tasks =
                        views.map {
                            TaskRow(
                                it.task.id,
                                it.task.title,
                                it.task.agent,
                                it.task.branch,
                                it.sidebarStatus,
                            )
                        },
                )
            }
    val layout = state.layout
    return copy(
        groups = groups,
        selected = layout.selectedTask?.takeIf { state[it] != null },
        sidebarWidth = layout.sidebarWidth ?: MainUiState.DEFAULT_SIDEBAR_WIDTH,
        diffOpen = layout.diffOpen,
        warnings = if (warningsDismissed) emptyList() else startupWarnings + state.warnings,
    )
}
