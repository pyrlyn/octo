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
