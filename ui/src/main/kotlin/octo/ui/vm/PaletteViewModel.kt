package octo.ui.vm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import octo.core.agent.Role
import octo.core.orchestrator.OctoCore
import octo.core.task.Task
import octo.core.task.TaskId
import octo.core.task.TranscriptEntry

/** What the palette can run besides opening a task. */
enum class Command(
    val label: String,
    /** Acts on the selected task, so it is offered only while one is. */
    val onTask: Boolean = false,
) {
    NewTask("New task"),
    Diff("Toggle diff", onTask = true),
    Publish("Publish", onTask = true),
    SwitchAgent("Switch agent", onTask = true),
    Fork("Fork", onTask = true),
    Move("Move to another machine", onTask = true),
    Stop("Stop agent", onTask = true),
    Agents("Open agents"),
    Usage("Open usage"),
    Devices("Open devices"),
}

sealed interface PaletteItem {
    data class Open(
        val id: TaskId,
        val title: String,
        /** The matching part of a message, when the title did not match. */
        val snippet: String? = null,
    ) : PaletteItem

    data class Run(
        val command: Command,
    ) : PaletteItem
}

/** A task as the palette searches it. */
data class Searchable(
    val id: TaskId,
    val title: String,
    /** The user's and the agent's messages, oldest first. */
    val messages: List<String>,
)

data class PaletteUiState(
    val query: String = "",
    val items: List<PaletteItem> = emptyList(),
    val selected: Int = 0,
)

/**
 * Tasks first, then commands. A task whose title starts with the query comes before one with a
 * word that does, then one that only contains it, then one whose messages contain it; ties keep
 * the given order. An empty query lists everything.
 */
internal fun rank(
    query: String,
    tasks: List<Searchable>,
    commands: List<Command>,
): List<PaletteItem> {
    val q = query.trim()
    if (q.isEmpty()) return tasks.map { PaletteItem.Open(it.id, it.title) } + commands.map { PaletteItem.Run(it) }
    val found =
        tasks.mapNotNull { task ->
            val title = task.title
            when {
                title.startsWith(q, ignoreCase = true) -> {
                    0 to PaletteItem.Open(task.id, title)
                }

                title.split(WORD_BREAK).any { it.startsWith(q, ignoreCase = true) } -> {
                    1 to PaletteItem.Open(task.id, title)
                }

                title.contains(q, ignoreCase = true) -> {
                    2 to PaletteItem.Open(task.id, title)
                }

                else -> {
                    task.messages
                        .lastOrNull { it.contains(q, ignoreCase = true) }
                        ?.let { 3 to PaletteItem.Open(task.id, title, snippet(it, q)) }
                }
            }
        }
    val matching =
        commands
            .filter { it.label.contains(q, ignoreCase = true) }
            .sortedBy { if (it.label.startsWith(q, ignoreCase = true)) 0 else 1 }
    return found.sortedBy { it.first }.map { it.second } + matching.map { PaletteItem.Run(it) }
}

/** The match with a little context on each side, on one line. */
internal fun snippet(
    text: String,
    query: String,
): String {
    val at = text.indexOf(query, ignoreCase = true)
    val start = (at - SNIPPET_CONTEXT).coerceAtLeast(0)
    val end = (at + query.length + SNIPPET_CONTEXT).coerceAtMost(text.length)
    val prefix = if (start > 0) "…" else ""
    val suffix = if (end < text.length) "…" else ""
    return prefix + text.substring(start, end).replace(WHITESPACE, " ") + suffix
}

private val WORD_BREAK = Regex("[^\\p{L}\\p{N}]+")
private val WHITESPACE = Regex("\\s+")
private const val SNIPPET_CONTEXT = 40

/** Cmd+K: finds a task by title or message text, or runs a [Command]. */
@OptIn(ExperimentalCoroutinesApi::class)
class PaletteViewModel(
    private val core: OctoCore,
    scope: CoroutineScope,
    /** The commands whose flags are on; task commands are dropped while no task is selected. */
    commands: List<Command>,
    selected: Task?,
) {
    private val scope = scope.childScope()
    private val commands =
        commands.filter { command ->
            when {
                !command.onTask -> true

                selected == null -> false

                // This computer's git and gh cannot reach a remote worktree.
                command == Command.Publish -> selected.machine == null

                else -> true
            }
        }
    private val query = MutableStateFlow("")
    private val mutable = MutableStateFlow(PaletteUiState())
    val state: StateFlow<PaletteUiState> = mutable.asStateFlow()

    init {
        // A search that fails leaves the last results; there is nothing better to show.
        this.scope.action({}) {
            combine(query, core.state) { q, octo -> q to octo.tasks }
                .mapLatest { (q, views) ->
                    val tasks = views.map { view -> Searchable(view.task.id, view.task.title, messages(view.task.id)) }
                    q to rank(q, tasks, this.commands)
                }.collect { (q, items) ->
                    // Results for a query the user has typed past are dropped.
                    mutable.update {
                        if (q !=
                            it.query
                        ) {
                            it
                        } else {
                            it.copy(
                                items = items,
                                selected = it.selected.coerceAtMost(items.lastIndex.coerceAtLeast(0)),
                            )
                        }
                    }
                }
        }
    }

    fun edit(text: String) {
        query.value = text
        mutable.update { it.copy(query = text, selected = 0) }
    }

    /** Moves the highlight by [by], stopping at either end. */
    fun move(by: Int) =
        mutable.update {
            it.copy(selected = (it.selected + by).coerceIn(0, it.items.lastIndex.coerceAtLeast(0)))
        }

    /** The highlighted item, for the caller to run. */
    fun chosen(): PaletteItem? = state.value.let { it.items.getOrNull(it.selected) }

    fun close() = scope.cancel()

    private suspend fun messages(id: TaskId): List<String> =
        core
            .transcript(id)
            .first()
            .filterIsInstance<TranscriptEntry.Text>()
            .filter { it.role == Role.User || it.role == Role.Assistant }
            .map { it.text }
}
