package octo.core.orchestrator

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import octo.core.status.AgentStatus
import octo.core.task.OctoState
import octo.core.task.TaskId

/**
 * Starts agents within their machine's [AgentSlots]. A start that gets no slot waits, and goes
 * ahead under its task's lock once one frees. The orchestrator knows how to launch an agent and
 * shows the task as [AgentStatus.Queued] meanwhile.
 */
internal class StartQueue(
    limits: Map<String?, Int>,
    private val scope: CoroutineScope,
    private val state: StateFlow<OctoState>,
    private val lock: suspend (TaskId, suspend () -> Unit) -> Unit,
    private val running: (TaskId) -> Boolean,
    private val launch: suspend (Waiting) -> Unit,
) {
    private val slots = AgentSlots(limits)

    fun limit(machine: String?): Int? = slots.limit(machine)

    /** Launches [start] when its machine has a free slot; false when it has to wait. Called under the task lock. */
    suspend fun start(start: Waiting): Boolean {
        if (!slots.claim(start)) return false
        launchHeld(start)
        return true
    }

    /** Launches a waiting task over its machine's limit; nothing when it is not waiting. Called under the task lock. */
    suspend fun startNow(id: TaskId) {
        slots.force(id)?.let { launchHeld(it) }
    }

    /** Frees [id]'s slot or place in the queue and starts the tasks that get a slot through it. */
    fun free(id: TaskId) {
        slots.release(id).forEach { next -> scope.launch { lock(next.id) { startWaiting(next) } } }
    }

    /** Starts nothing more; returns the tasks that were waiting. */
    fun close(): List<TaskId> = slots.close().map { it.id }

    private suspend fun launchHeld(start: Waiting) {
        try {
            launch(start)
        } finally {
            // An agent that did not start gives its slot to the next task at once.
            if (!running(start.id)) free(start.id)
        }
    }

    // The task may have been stopped or removed while it waited for the lock.
    private suspend fun startWaiting(start: Waiting) {
        if (state.value[start.id]?.status == AgentStatus.Queued) launchHeld(start) else free(start.id)
    }
}
