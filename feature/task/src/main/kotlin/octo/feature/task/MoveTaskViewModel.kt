package octo.feature.task

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import octo.core.orchestrator.OctoCore
import octo.core.task.TaskId
import octo.feature.common.action
import octo.feature.common.childScope
import octo.feature.common.userMessage

data class MoveTaskUiState(
    /** The machines that have a clone of the task's repository; null is this computer. */
    val targets: List<String?> = emptyList(),
    val target: String? = null,
    /** Whether [target] was picked, since null is a target too. */
    val chosen: Boolean = false,
    val moving: Boolean = false,
    val done: Boolean = false,
    val error: String? = null,
) {
    val canMove: Boolean get() = chosen && !moving
}

/** Moves a task with its branch and uncommitted work to another machine. */
class MoveTaskViewModel(
    private val core: OctoCore,
    private val taskId: TaskId,
    private val parent: CoroutineScope,
) {
    private val scope = parent.childScope()
    private val mutable = MutableStateFlow(MoveTaskUiState())
    val state: StateFlow<MoveTaskUiState> = mutable.asStateFlow()

    init {
        val targets = core.moveTargets(taskId)
        mutable.update { it.copy(targets = targets, target = targets.singleOrNull(), chosen = targets.size == 1) }
    }

    fun select(machine: String?) = mutable.update { it.copy(target = machine, chosen = true) }

    fun move() {
        val s = state.value.takeIf { it.canMove } ?: return
        mutable.update { it.copy(moving = true, error = null) }
        // Closing the dialog must not cut a move short and leave the task between two machines.
        parent.action(::fail) {
            core.moveTask(taskId, s.target)
            mutable.update { it.copy(moving = false, done = true) }
        }
    }

    fun close() = scope.cancel()

    private fun fail(e: Exception) = mutable.update { it.copy(moving = false, error = e.userMessage) }
}
