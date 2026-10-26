package octo.ui.vm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import octo.core.agent.AgentId
import octo.core.agent.VendorTerms
import octo.core.orchestrator.AgentCatalog
import octo.core.orchestrator.AgentInfo
import octo.core.orchestrator.OctoCore
import octo.core.preset.Preset
import octo.core.preset.PresetLibrary
import octo.core.preset.startPreset
import octo.core.task.NewTask
import octo.core.task.TaskId
import octo.core.terms.TermsNotAcknowledgedException

data class TermsNotice(
    val agent: AgentInfo,
    val terms: VendorTerms,
)

data class NewTaskUiState(
    val repo: String = "",
    val baseRef: String = DEFAULT_BASE_REF,
    val title: String = "",
    val prompt: String = "",
    /** Remote machines; the choice is shown only when there are some. */
    val machines: List<String> = emptyList(),
    /** Null runs the task on this computer. */
    val machine: String? = null,
    val agents: List<AgentInfo> = emptyList(),
    val agent: AgentId? = null,
    val loadingAgents: Boolean = true,
    val creating: Boolean = false,
    /** Shown before the first task with an agent whose vendor's terms need a decision. */
    val terms: TermsNotice? = null,
    val created: TaskId? = null,
    val error: String? = null,
    /** Whether presets can be used at all; false while the `presets` flag is off. */
    val presetsEnabled: Boolean = false,
    /** Start a saved team instead of one agent. */
    val team: Boolean = false,
    val presets: List<Preset> = emptyList(),
    val preset: String? = null,
) {
    val selectedAgent: AgentInfo? get() = agents.firstOrNull { it.id == agent }

    val selectedPreset: Preset? get() = presets.firstOrNull { it.name == preset }

    /** Members whose agent is not installed on the chosen machine; the team cannot start until there are none. */
    val missingAgents: List<AgentId>
        get() =
            selectedPreset
                ?.members
                ?.map { it.agent }
                ?.distinct()
                ?.filter { id -> agents.none { it.id == id && it.probe.installed } }
                .orEmpty()

    /** The title, or the prompt's first line when the title is left empty. */
    val effectiveTitle: String get() =
        title.trim().ifEmpty {
            prompt
                .trim()
                .lineSequence()
                .firstOrNull()
                .orEmpty()
                .take(TITLE_LENGTH)
        }

    val canCreate: Boolean
        get() =
            !creating && repo.isNotBlank() && baseRef.isNotBlank() &&
                if (team) {
                    selectedPreset != null && !loadingAgents && missingAgents.isEmpty()
                } else {
                    effectiveTitle.isNotEmpty() && selectedAgent?.probe?.installed == true
                }

    companion object {
        const val DEFAULT_BASE_REF = "main"
        const val TITLE_LENGTH = 60
    }
}

/** The new-task form: repository, base branch, agent and the first prompt. */
class NewTaskViewModel(
    private val core: OctoCore,
    private val catalog: AgentCatalog,
    parent: CoroutineScope,
    /** Null while the `presets` flag is off. */
    library: PresetLibrary? = null,
) {
    private val scope = parent.childScope()
    private val mutable =
        MutableStateFlow(NewTaskUiState(machines = catalog.machines, presetsEnabled = library != null))
    val state: StateFlow<NewTaskUiState> = mutable.asStateFlow()

    init {
        loadAgents(null)
        library?.let {
            scope.launch {
                it.presets.collect { all ->
                    mutable.update { s ->
                        s.copy(presets = all, preset = s.preset?.takeIf { name -> all.any { it.name == name } })
                    }
                }
            }
        }
    }

    fun useTeam(on: Boolean) = mutable.update { it.copy(team = on && it.presetsEnabled, error = null) }

    fun selectPreset(name: String) = mutable.update { it.copy(preset = name) }

    /** Agents are probed on the chosen machine, so the list is reloaded whenever it changes. */
    fun selectMachine(id: String?) {
        if (id == state.value.machine) return
        mutable.update { it.copy(machine = id, agents = emptyList(), agent = null, loadingAgents = true, error = null) }
        loadAgents(id)
    }

    private fun loadAgents(machine: String?) {
        scope.action(::fail) {
            val agents = catalog.agents(machine)
            mutable.update { s ->
                // A slow probe of a machine the user already switched away from must not win.
                if (s.machine != machine) {
                    s
                } else {
                    s.copy(
                        loadingAgents = false,
                        agents = agents,
                        agent = s.agent ?: agents.firstOrNull { it.probe.installed }?.id,
                    )
                }
            }
        }
    }

    fun editRepo(value: String) = mutable.update { it.copy(repo = value) }

    fun editBaseRef(value: String) = mutable.update { it.copy(baseRef = value) }

    fun editTitle(value: String) = mutable.update { it.copy(title = value) }

    fun editPrompt(value: String) = mutable.update { it.copy(prompt = value) }

    fun selectAgent(id: AgentId) = mutable.update { it.copy(agent = id) }

    fun create() {
        val s = state.value.takeIf { it.canCreate } ?: return
        if (s.team) createTeam(s) else createOne(s)
    }

    // Every member's vendor terms are settled before the first task starts, so a team never
    // ends up half started because one agent still needs a decision.
    private fun createTeam(s: NewTaskUiState) {
        val preset = s.selectedPreset ?: return
        mutable.update { it.copy(creating = true, error = null) }
        scope.action(::fail) {
            for (id in preset.members.map { it.agent }.distinct()) {
                val terms = catalog.pendingTerms(id) ?: continue
                val agent = s.agents.first { it.id == id }
                mutable.update { it.copy(creating = false, terms = TermsNotice(agent, terms)) }
                return@action
            }
            val ids = core.startPreset(preset, s.repo.trim(), s.baseRef.trim(), s.machine)
            mutable.update { it.copy(creating = false, created = ids.firstOrNull()) }
        }
    }

    private fun createOne(s: NewTaskUiState) {
        val agent = s.selectedAgent ?: return
        mutable.update { it.copy(creating = true, error = null) }
        scope.action(::fail) {
            try {
                val id =
                    core.createTask(
                        NewTask(
                            repo = s.repo.trim(),
                            baseRef = s.baseRef.trim(),
                            agent = agent.id,
                            title = s.effectiveTitle,
                            prompt = s.prompt.trim().ifEmpty { null },
                            machine = s.machine,
                        ),
                    )
                mutable.update { it.copy(creating = false, created = id) }
            } catch (e: TermsNotAcknowledgedException) {
                mutable.update { it.copy(creating = false, terms = TermsNotice(agent, e.terms)) }
            }
        }
    }

    /** The user read the vendor's terms and goes ahead; the acknowledgement is stored by core. */
    fun acceptTerms() {
        val notice = state.value.terms ?: return
        mutable.update { it.copy(terms = null) }
        scope.action(::fail) {
            catalog.acknowledgeTerms(notice.agent.id)
            create()
        }
    }

    fun declineTerms() = mutable.update { it.copy(terms = null) }

    fun dismissError() = mutable.update { it.copy(error = null) }

    fun close() = scope.cancel()

    private fun fail(e: Exception) =
        mutable.update {
            it.copy(loadingAgents = false, creating = false, error = e.userMessage)
        }
}
