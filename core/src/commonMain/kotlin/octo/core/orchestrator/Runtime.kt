package octo.core.orchestrator

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import octo.core.agent.AgentSession
import octo.core.agent.Capability
import octo.core.status.AgentState
import octo.core.task.OctoState
import octo.core.task.TaskId
import kotlin.coroutines.cancellation.CancellationException

/**
 * One running agent; [scope] is the task's own supervisor, ended when the agent stops. [run]
 * tells checkpoints of this session apart from those of an earlier one.
 */
internal class Runtime(
    val session: AgentSession,
    val agent: MutableStateFlow<AgentState>,
    val capabilities: Set<Capability>,
    parent: CoroutineScope,
    val run: String = randomId(),
) {
    val supervisor = SupervisorJob(parent.coroutineContext[Job])
    val scope = CoroutineScope(parent.coroutineContext + supervisor)
    lateinit var job: Job
}

/**
 * Adds [text] to the running turn; false when it could not. Any failure means the turn could not
 * take the text, and the queue is the declared fallback.
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
internal suspend fun Runtime.steer(text: String): Boolean =
    Capability.Steer in capabilities &&
        try {
            session.steer(text)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }

/** The root process of each agent on this computer; a remote task's is the ssh client, whose figures would mislead. */
internal fun localRoots(
    state: OctoState,
    runtimes: Map<TaskId, Runtime>,
): Map<TaskId, Long> {
    val local = state.tasks.filter { it.task.machine == null }.map { it.task.id }
    return runtimes
        .filterKeys { it in local }
        .mapNotNull { (id, runtime) -> runtime.session.processId?.let { id to it } }
        .toMap()
}
