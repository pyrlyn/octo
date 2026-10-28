package octo.ui.vm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import octo.core.agent.AgentId
import octo.core.agent.PermissionMode
import octo.core.agent.VendorTerms
import octo.core.attachments.FileUpload
import octo.core.compare.AgentPick
import octo.core.compare.compare
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
    /** Always one of [branches] once they are known. */
    val baseRef: String = DEFAULT_BASE_REF,
    /** Branches of [repo] on the chosen machine; the base branch is picked from them. */
    val branches: List<String> = emptyList(),
    val loadingBranches: Boolean = false,
    /** Why [repo] has no branches to offer, e.g. it is not a git repository. */
    val branchesError: String? = null,
    val title: String = "",
    val prompt: String = "",
    /** Files for the first prompt; a team's prompts come from its preset, so it takes none. */
    val files: List<FileUpload> = emptyList(),
    /** Whether files can be attached; false while the `attachments` flag is off. */
    val canAttach: Boolean = false,
    /** Remote machines; the choice is shown only when there are some. */
    val machines: List<String> = emptyList(),
    /** Null runs the task on this computer. */
    val machine: String? = null,
    val agents: List<AgentInfo> = emptyList(),
    val agent: AgentId? = null,
    /** Null takes the default from the config. */
    val permissionMode: PermissionMode? = null,
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
    /** Whether a prompt can go to several agents at once; false while the `compare` flag is off. */
    val compareEnabled: Boolean = false,
    /** Send the prompt to several agents, each in its own task, instead of one. */
    val compare: Boolean = false,
    /** Agents chosen for the comparison, each with its models as typed: comma-separated, empty for the default. */
    val compared: Map<AgentId, String> = emptyMap(),
) {
    val selectedAgent: AgentInfo? get() = agents.firstOrNull { it.id == agent }

    /** The selected agent's modes; offered only when it has a choice. */
    val permissionModes: List<PermissionMode>
        get() = PermissionMode.entries.filter { it in selectedAgent?.permissionModes.orEmpty() }

    /** A mode the agent lacks, left over from another agent, falls back to the config default. */
    val chosenPermissionMode: PermissionMode? get() = permissionMode?.takeIf { it in permissionModes }

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

    /** One pick per chosen agent and model, in the order the agents are listed. */
    val picks: List<AgentPick>
        get() =
            agents.filter { it.id in compared }.flatMap { agent ->
                compared
                    .getValue(agent.id)
                    .split(',')
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .distinct()
                    .ifEmpty { listOf(null) }
                    .map { AgentPick(agent.id, it) }
            }

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
            !creating && repo.isNotBlank() && baseRef in branches &&
                when {
                    team -> {
                        selectedPreset != null && !loadingAgents && missingAgents.isEmpty()
                    }

                    compare -> {
                        effectiveTitle.isNotEmpty() && prompt.isNotBlank() && picks.size >= MIN_PICKS &&
                            agents.filter { it.id in compared }.all { it.probe.installed }
                    }

                    else -> {
                        effectiveTitle.isNotEmpty() && selectedAgent?.probe?.installed == true
                    }
                }

    companion object {
        const val DEFAULT_BASE_REF = "main"
        const val TITLE_LENGTH = 60

        /** A comparison needs something to compare against. */
        const val MIN_PICKS = 2

        /** How long typing in the repository field must pause before git is asked for its branches. */
        const val BRANCH_LOOKUP_DELAY_MS = 300L
    }
}

/** The new-task form: repository, base branch, agent and the first prompt. */
class NewTaskViewModel(
    private val core: OctoCore,
    private val catalog: AgentCatalog,
    parent: CoroutineScope,
    /** Null while the `presets` flag is off. */
    library: PresetLibrary? = null,
    /** False while the `compare` flag is off. */
    compare: Boolean = false,
    /** False while the `attachments` flag is off. */
    attachments: Boolean = false,
) {
    private val scope = parent.childScope()
    private val mutable =
        MutableStateFlow(
            NewTaskUiState(
                machines = catalog.machines,
                presetsEnabled = library != null,
                compareEnabled = compare,
                canAttach = attachments,
            ),
        )
    val state: StateFlow<NewTaskUiState> = mutable.asStateFlow()
    private var branchLookup: Job? = null

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

    fun useTeam(on: Boolean) = mutable.update { it.copy(team = on && it.presetsEnabled, compare = false, error = null) }

    fun useCompare(on: Boolean) =
        mutable.update { it.copy(compare = on && it.compareEnabled, team = false, error = null) }

    fun toggleCompared(id: AgentId) =
        mutable.update { it.copy(compared = if (id in it.compared) it.compared - id else it.compared + (id to "")) }

    /** Comma-separated models for [id]; one task each. */
    fun editComparedModels(
        id: AgentId,
        models: String,
    ) = mutable.update { if (id in it.compared) it.copy(compared = it.compared + (id to models)) else it }

    fun selectPreset(name: String) = mutable.update { it.copy(preset = name) }

    /** Agents are probed on the chosen machine, so the list is reloaded whenever it changes. */
    fun selectMachine(id: String?) {
        if (id == state.value.machine) return
        mutable.update {
            it.copy(
                machine = id,
                agents = emptyList(),
                agent = null,
                compared = emptyMap(),
                loadingAgents = true,
                error = null,
            )
        }
        loadAgents(id)
        loadBranches()
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

    fun editRepo(value: String) {
        mutable.update { it.copy(repo = value) }
        loadBranches()
    }

    /** Only a branch the repository has can be the base. */
    fun selectBaseRef(value: String) = mutable.update { if (value in it.branches) it.copy(baseRef = value) else it }

    // The repository path changes on every key, so git is asked only after a pause, and an answer
    // for a path or machine the user has since left is dropped.
    private fun loadBranches() {
        branchLookup?.cancel()
        val repo = state.value.repo.trim()
        val machine = state.value.machine
        mutable.update { it.copy(branches = emptyList(), loadingBranches = repo.isNotEmpty(), branchesError = null) }
        if (repo.isEmpty()) return

        fun current(s: NewTaskUiState) = s.repo.trim() == repo && s.machine == machine
        branchLookup =
            scope.action({ e ->
                mutable.update {
                    if (current(
                            it,
                        )
                    ) {
                        it.copy(loadingBranches = false, branchesError = e.userMessage)
                    } else {
                        it
                    }
                }
            }) {
                delay(NewTaskUiState.BRANCH_LOOKUP_DELAY_MS)
                val branches = core.branches(repo, machine)
                mutable.update { s ->
                    if (!current(s)) {
                        s
                    } else {
                        s.copy(branches = branches, loadingBranches = false, baseRef = defaultBase(s.baseRef, branches))
                    }
                }
            }
    }

    private fun defaultBase(
        current: String,
        branches: List<String>,
    ) = current.takeIf { it in branches }
        ?: NewTaskUiState.DEFAULT_BASE_REF.takeIf { it in branches }
        ?: branches.firstOrNull().orEmpty()

    fun editTitle(value: String) = mutable.update { it.copy(title = value) }

    fun editPrompt(value: String) = mutable.update { it.copy(prompt = value) }

    val files = FilePicker({ edit -> mutable.update { it.copy(files = edit(it.files)) } }, ::showError)

    fun selectAgent(id: AgentId) = mutable.update { it.copy(agent = id) }

    /** Null goes back to the config default. */
    fun selectPermissionMode(mode: PermissionMode?) = mutable.update { it.copy(permissionMode = mode) }

    fun create() {
        val s = state.value.takeIf { it.canCreate } ?: return
        when {
            s.team -> createTeam(s)
            s.compare -> createComparison(s)
            else -> createOne(s)
        }
    }

    // As with a team, every agent's vendor terms are settled before the first task starts. A pick
    // that still fails keeps the form open with what did not start; the others are already running.
    private fun createComparison(s: NewTaskUiState) {
        val picks = s.picks
        mutable.update { it.copy(creating = true, error = null) }
        scope.action(::fail) {
            for (id in picks.map { it.agent }.distinct()) {
                val terms = catalog.pendingTerms(id) ?: continue
                val agent = s.agents.first { it.id == id }
                mutable.update { it.copy(creating = false, terms = TermsNotice(agent, terms)) }
                return@action
            }
            val request =
                NewTask(
                    repo = s.repo.trim(),
                    baseRef = s.baseRef.trim(),
                    agent = picks.first().agent,
                    title = s.effectiveTitle,
                    prompt = s.prompt.trim(),
                    files = s.files,
                    machine = s.machine,
                )
            val result = core.compare(request, picks)
            val failed = result.failures.joinToString("; ") { (pick, why) -> "${pick.label}: $why" }
            mutable.update {
                if (failed.isEmpty()) {
                    it.copy(creating = false, created = result.tasks.firstOrNull())
                } else {
                    it.copy(
                        creating = false,
                        error = "Started ${result.tasks.size} of ${picks.size}. Not started: $failed",
                    )
                }
            }
        }
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
                            prompt = s.prompt.trim().takeIf { it.isNotEmpty() || s.files.isNotEmpty() },
                            files = s.files,
                            machine = s.machine,
                            permissionMode = s.chosenPermissionMode,
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

    private fun showError(reason: String) = mutable.update { it.copy(error = reason) }

    fun close() = scope.cancel()

    private fun fail(e: Exception) =
        mutable.update {
            it.copy(loadingAgents = false, creating = false, error = e.userMessage)
        }
}
