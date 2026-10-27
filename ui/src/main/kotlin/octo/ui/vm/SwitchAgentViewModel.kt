package octo.ui.vm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import octo.core.agent.AgentId
import octo.core.agent.MODEL_OPTION
import octo.core.orchestrator.AgentCatalog
import octo.core.orchestrator.AgentInfo
import octo.core.orchestrator.OctoCore
import octo.core.task.TaskId

data class SwitchAgentUiState(
    val agents: List<AgentInfo> = emptyList(),
    val loading: Boolean = true,
    val current: AgentId? = null,
    val currentModel: String = "",
    val agent: AgentId? = null,
    /** Empty leaves the choice to the agent. */
    val model: String = "",
    val switching: Boolean = false,
    val done: Boolean = false,
    val terms: TermsNotice? = null,
    val error: String? = null,
) {
    val selectedAgent: AgentInfo? get() = agents.firstOrNull { it.id == agent }

    /** A model switch stays in the running session; anything else hands the conversation over. */
    val handsOver: Boolean get() = agent != current

    val canSwitch: Boolean
        get() =
            !switching && selectedAgent?.probe?.installed == true &&
                (agent != current || model.trim() != currentModel)
}

/** Moves a task to another agent or model; the worktree and branch stay. */
class SwitchAgentViewModel(
    private val core: OctoCore,
    private val catalog: AgentCatalog,
    private val taskId: TaskId,
    parent: CoroutineScope,
) {
    private val scope = parent.childScope()
    private val mutable = MutableStateFlow(SwitchAgentUiState())
    val state: StateFlow<SwitchAgentUiState> = mutable.asStateFlow()

    init {
        val task = core.state.value[taskId]?.task
        val model = task?.options?.get(MODEL_OPTION).orEmpty()
        mutable.update { it.copy(current = task?.agent, currentModel = model, agent = task?.agent, model = model) }
        scope.action(::fail) {
            val agents = catalog.agents(task?.machine)
            mutable.update { it.copy(agents = agents, loading = false) }
        }
    }

    // Another agent's model name means nothing to the new one, so the field starts empty there.
    fun selectAgent(id: AgentId) =
        mutable.update { it.copy(agent = id, model = if (id == it.current) it.currentModel else "") }

    fun editModel(value: String) = mutable.update { it.copy(model = value) }

    fun switch() {
        val s = state.value.takeIf { it.canSwitch } ?: return
        val agent = s.selectedAgent ?: return
        mutable.update { it.copy(switching = true, error = null) }
        scope.action(::fail) {
            catalog.pendingTerms(agent.id)?.let { terms ->
                mutable.update { it.copy(switching = false, terms = TermsNotice(agent, terms)) }
                return@action
            }
            core.switchAgent(taskId, agent.id, s.model.trim().ifEmpty { null })
            mutable.update { it.copy(switching = false, done = true) }
        }
    }

    /** The user read the vendor's terms and goes ahead; the acknowledgement is stored by core. */
    fun acceptTerms() {
        val notice = state.value.terms ?: return
        mutable.update { it.copy(terms = null) }
        scope.action(::fail) {
            catalog.acknowledgeTerms(notice.agent.id)
            switch()
        }
    }

    fun declineTerms() = mutable.update { it.copy(terms = null) }

    fun close() = scope.cancel()

    private fun fail(e: Exception) =
        mutable.update { it.copy(loading = false, switching = false, error = e.userMessage) }
}
