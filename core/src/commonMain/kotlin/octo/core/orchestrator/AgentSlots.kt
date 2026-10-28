package octo.core.orchestrator

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import octo.core.agent.AgentSessionRef
import octo.core.task.TaskId

/** How a waiting task's agent is to start once it gets a slot. */
internal data class Waiting(
    val id: TaskId,
    val machine: String?,
    val resume: Boolean,
    val forkFrom: AgentSessionRef?,
)

/**
 * The cap on live agents per machine (null for this computer) and the tasks waiting for a slot,
 * in the order they asked. A machine without a limit never queues. All changes go through one
 * atomic update, so two agents ending at once never hand out the same slot twice.
 */
internal class AgentSlots(
    private val limits: Map<String?, Int>,
) {
    private data class Slots(
        val held: Map<TaskId, String?> = emptyMap(),
        val waiting: List<Waiting> = emptyList(),
        val closed: Boolean = false,
    )

    private val slots = MutableStateFlow(Slots())

    fun limit(machine: String?): Int? = limits[machine]

    fun isWaiting(id: TaskId) = slots.value.waiting.any { it.id == id }

    /** True when [start] may go ahead now; otherwise it joins the end of the queue. */
    fun claim(start: Waiting): Boolean {
        var granted = false
        slots.update { s ->
            val limit = limits[start.machine]
            granted = start.id in s.held || limit == null || s.held.count { it.value == start.machine } < limit
            when {
                granted -> s.copy(held = s.held + (start.id to start.machine), waiting = s.waiting.without(start.id))
                s.waiting.any { it.id == start.id } -> s
                else -> s.copy(waiting = s.waiting + start)
            }
        }
        return granted
    }

    /** Frees [id]'s slot or place in the queue; returns the waiting starts that now hold a slot. */
    fun release(id: TaskId): List<Waiting> {
        var promoted = emptyList<Waiting>()
        slots.update { s ->
            val left = s.copy(held = s.held - id, waiting = s.waiting.without(id))
            promoted = if (left.closed) emptyList() else left.next()
            left.copy(
                held = left.held + promoted.map { it.id to it.machine },
                waiting = left.waiting - promoted.toSet(),
            )
        }
        return promoted
    }

    /** Takes [id] out of the queue and gives it a slot over the limit; null when it was not waiting. */
    fun force(id: TaskId): Waiting? {
        var forced: Waiting? = null
        slots.update { s ->
            forced = s.waiting.firstOrNull { it.id == id }
            forced?.let { s.copy(held = s.held + (it.id to it.machine), waiting = s.waiting.without(id)) } ?: s
        }
        return forced
    }

    /** Hands out no more slots and returns the tasks that were waiting. */
    fun close(): List<Waiting> {
        var waiting = emptyList<Waiting>()
        slots.update { s ->
            waiting = s.waiting
            s.copy(waiting = emptyList(), closed = true)
        }
        return waiting
    }

    private fun Slots.next(): List<Waiting> {
        val free = limits.mapValues { (machine, limit) -> limit - held.count { it.value == machine } }.toMutableMap()
        return waiting.filter { w ->
            val left = free[w.machine] ?: 0
            (left > 0).also { if (it) free[w.machine] = left - 1 }
        }
    }

    private fun List<Waiting>.without(id: TaskId) = filterNot { it.id == id }
}
