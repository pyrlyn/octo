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
import octo.core.task.TaskPlace
import octo.core.task.TaskView
import octo.core.task.rollup

data class TaskRow(
    val id: TaskId,
    val title: String,
    val agent: AgentId,
    val branch: String,
    val status: SidebarStatus,
    /** Generations of forks above this task in the list; 0 for a task that is not a fork. */
    val depth: Int = 0,
    /** Set on the first row of a comparison: the tasks below it, one level deeper, share one prompt. */
    val groupTitle: String? = null,
    val place: TaskPlace = TaskPlace.Active,
    /** Set on the first row of each section, while a repository's tasks sit in more than one. */
    val sectionTitle: String? = null,
)

/** Sidebar sections, in the order they are listed. */
enum class Section(
    val title: String,
) {
    Pinned("Pinned"),
    Active("Active"),
    Snoozed("Snoozed"),
    Settled("Settled"),
    ;

    companion object {
        fun of(place: TaskPlace) =
            when (place) {
                TaskPlace.Pinned -> Pinned
                TaskPlace.Active -> Active
                is TaskPlace.Snoozed -> Snoozed
                TaskPlace.Settled -> Settled
            }
    }
}

data class RepoGroup(
    val repo: String,
    val name: String,
    val status: SidebarStatus?,
    val tasks: List<TaskRow>,
)

enum class TaskDialog { Publish, SwitchAgent }

data class MainUiState(
    val groups: List<RepoGroup> = emptyList(),
    val selected: TaskId? = null,
    val sidebarWidth: Float = DEFAULT_SIDEBAR_WIDTH,
    val diffOpen: Boolean = false,
    val newTaskOpen: Boolean = false,
    /** The running-agents screen replaces the selected task until a task is picked or it is closed. */
    val agentsOpen: Boolean = false,
    /** A dialog over the selected task; picking another task closes it. */
    val taskDialog: TaskDialog? = null,
    val devicesOpen: Boolean = false,
    val paletteOpen: Boolean = false,
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
    /** False while the `sections` flag is off: one flat list, and places cannot be changed. */
    val sections: Boolean = false,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutable = MutableStateFlow(MainUiState().withCore(core.state.value, startupWarnings, sections))
    val state: StateFlow<MainUiState> = mutable.asStateFlow()

    init {
        act { core.state.collect { octo -> mutable.update { it.withCore(octo, startupWarnings, sections) } } }
    }

    fun select(id: TaskId) {
        closeAgents()
        mutable.update { s -> s.copy(taskDialog = s.taskDialog.takeIf { id == s.selected }) }
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

    fun openAgents() = mutable.update { it.copy(agentsOpen = true) }

    fun closeAgents() = mutable.update { it.copy(agentsOpen = false) }

    fun closeNewTask() = mutable.update { it.copy(newTaskOpen = false) }

    fun openTaskDialog(dialog: TaskDialog) = mutable.update { it.copy(taskDialog = dialog) }

    fun closeTaskDialog() = mutable.update { it.copy(taskDialog = null) }

    fun openDevices() = mutable.update { it.copy(devicesOpen = true) }

    fun closeDevices() = mutable.update { it.copy(devicesOpen = false) }

    fun togglePalette() = mutable.update { it.copy(paletteOpen = !it.paletteOpen) }

    fun closePalette() = mutable.update { it.copy(paletteOpen = false) }

    fun stop(id: TaskId) = act { core.stop(id) }

    /** Runs a palette [command]; one that needs a task does nothing while none is selected. */
    fun run(command: Command) {
        val selected = state.value.selected
        when (command) {
            Command.NewTask -> openNewTask()
            Command.Agents -> openAgents()
            Command.Devices -> openDevices()
            Command.Diff -> selected?.let { showDiff(!state.value.diffOpen) }
            Command.Publish -> selected?.let { openTaskDialog(TaskDialog.Publish) }
            Command.SwitchAgent -> selected?.let { openTaskDialog(TaskDialog.SwitchAgent) }
            Command.Fork -> selected?.let(::fork)
            Command.Stop -> selected?.let(::stop)
        }
    }

    /** Forks [id] and opens the fork. */
    fun fork(id: TaskId) = act { select(core.forkTask(id)) }

    fun place(
        id: TaskId,
        place: TaskPlace,
    ) = act { core.setPlace(id, place) }

    /** Snoozes [id] for [forMs], or with null until its agent's turn ends. */
    fun snooze(
        id: TaskId,
        forMs: Long?,
    ) = place(id, TaskPlace.Snoozed(forMs?.let { now() + it }))

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
    sections: Boolean,
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
                        grouped(lineage(views))
                            .map { (it, depth, groupTitle) ->
                                TaskRow(
                                    it.task.id,
                                    it.task.title,
                                    it.task.agent,
                                    it.task.branch,
                                    it.sidebarStatus,
                                    depth,
                                    groupTitle,
                                    it.task.place,
                                )
                            }.let { if (sections) sectioned(it) else it },
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

/**
 * Forks right under their parent, one generation deeper each; a fork whose parent is gone is
 * listed as a task of its own.
 */
private fun lineage(views: List<TaskView>): List<Pair<TaskView, Int>> {
    val ids = views.map { it.task.id }.toSet()
    val children = views.groupBy { view -> view.task.parentId?.takeIf { it in ids } }

    fun walk(
        parent: TaskId?,
        depth: Int,
    ): List<Pair<TaskView, Int>> =
        children[parent].orEmpty().flatMap {
            listOf(it to depth) +
                walk(it.task.id, depth + 1)
        }

    val placed = walk(null, 0)
    // A parent loop in a hand-edited state file must not hide its tasks.
    val seen = placed.map { it.first.task.id }.toSet()
    return placed + views.filter { it.task.id !in seen }.map { it to 0 }
}

/**
 * Tasks of one comparison, each with its forks, gathered where the first of them is listed and
 * one level deeper, under the group's title.
 */
private fun grouped(rows: List<Pair<TaskView, Int>>): List<Triple<TaskView, Int, String?>> {
    val blocks =
        rows.fold(mutableListOf<MutableList<Pair<TaskView, Int>>>()) { acc, row ->
            if (row.second == 0 || acc.isEmpty()) acc += mutableListOf(row) else acc.last() += row
            acc
        }
    val byGroup =
        blocks.groupBy {
            it
                .first()
                .first.task.group
                ?.id
        }
    val placed = mutableSetOf<String>()
    return blocks.flatMap { block ->
        val group =
            block
                .first()
                .first.task.group
        when {
            group == null -> {
                block.map { (view, depth) -> Triple(view, depth, null) }
            }

            !placed.add(group.id) -> {
                emptyList()
            }

            else -> {
                byGroup.getValue(group.id).flatten().mapIndexed { i, (view, depth) ->
                    Triple(view, depth + 1, group.title.takeIf { i == 0 })
                }
            }
        }
    }
}

/** Each task with its forks, or each comparison, moves to the section of its first task. */
private fun sectioned(rows: List<TaskRow>): List<TaskRow> {
    val blocks =
        rows.fold(mutableListOf<MutableList<TaskRow>>()) { acc, row ->
            if (row.depth == 0 || row.groupTitle != null ||
                acc.isEmpty()
            ) {
                acc += mutableListOf(row)
            } else {
                acc.last() += row
            }
            acc
        }
    val bySection = blocks.groupBy { Section.of(it.first().place) }.toSortedMap()
    if (bySection.size < 2) return rows
    return bySection.flatMap { (section, inSection) ->
        inSection.flatten().mapIndexed { i, row -> if (i == 0) row.copy(sectionTitle = section.title) else row }
    }
}
