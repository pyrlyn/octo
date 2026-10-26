package octo.ui.vm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import octo.core.agent.AgentId
import octo.core.orchestrator.AgentCatalog
import octo.core.orchestrator.AgentInfo
import octo.core.preset.Preset
import octo.core.preset.PresetLibrary
import octo.core.preset.PresetMember

/** A preset being edited; [original] is the saved name it replaces, null for a new one. */
data class PresetDraft(
    val original: String?,
    val name: String = original.orEmpty(),
    val members: List<PresetMember> = emptyList(),
) {
    val preset: Preset get() = Preset(name, members)
}

data class PresetsUiState(
    val presets: List<Preset> = emptyList(),
    val agents: List<AgentInfo> = emptyList(),
    val draft: PresetDraft? = null,
    val saving: Boolean = false,
    val error: String? = null,
) {
    val canSave: Boolean get() = !saving && draft?.preset?.problem() == null
}

/** The preset editor: lists, creates, edits and deletes saved presets. */
class PresetsViewModel(
    private val library: PresetLibrary,
    private val catalog: AgentCatalog,
    parent: CoroutineScope,
) {
    private val scope = parent.childScope()
    private val mutable = MutableStateFlow(PresetsUiState())
    val state: StateFlow<PresetsUiState> = mutable.asStateFlow()

    init {
        scope.launch { library.presets.collect { all -> mutable.update { it.copy(presets = all) } } }
        // Presets name agents on this computer; a member can still run on a remote machine that has them.
        scope.action(::fail) {
            val agents = catalog.agents()
            mutable.update { it.copy(agents = agents) }
        }
    }

    fun create() =
        mutable.update {
            it.copy(draft = PresetDraft(original = null, members = listOf(member(it, 0))), error = null)
        }

    fun edit(name: String) {
        val preset = state.value.presets.firstOrNull { it.name == name } ?: return
        mutable.update { it.copy(draft = PresetDraft(name, members = preset.members), error = null) }
    }

    fun editName(value: String) = draft { it.copy(name = value) }

    fun addMember() =
        mutable.update { s ->
            val draft = s.draft ?: return@update s
            s.copy(draft = draft.copy(members = draft.members + member(s, draft.members.size)))
        }

    fun removeMember(index: Int) = draft { d -> d.copy(members = d.members.filterIndexed { i, _ -> i != index }) }

    fun editRole(
        index: Int,
        value: String,
    ) = member(index) { it.copy(role = value) }

    fun selectAgent(
        index: Int,
        agent: AgentId,
    ) = member(index) { it.copy(agent = agent) }

    fun editPrompt(
        index: Int,
        value: String,
    ) = member(index) { it.copy(prompt = value) }

    fun cancel() = mutable.update { it.copy(draft = null, error = null) }

    fun save() {
        val s = state.value.takeIf { it.canSave } ?: return
        val draft = s.draft ?: return
        mutable.update { it.copy(saving = true, error = null) }
        scope.action(::fail) {
            library.put(draft.preset, replacing = draft.original)
            mutable.update { it.copy(saving = false, draft = null) }
        }
    }

    fun delete(name: String) = scope.action(::fail) { library.remove(name) }

    fun dismissError() = mutable.update { it.copy(error = null) }

    fun close() = scope.cancel()

    private fun draft(change: (PresetDraft) -> PresetDraft) =
        mutable.update { s -> s.copy(draft = s.draft?.let(change)) }

    private fun member(
        index: Int,
        change: (PresetMember) -> PresetMember,
    ) = draft { d -> d.copy(members = d.members.mapIndexed { i, m -> if (i == index) change(m) else m }) }

    // A new member starts on the first installed agent, so the common case needs no extra click.
    private fun member(
        s: PresetsUiState,
        count: Int,
    ): PresetMember {
        val agent = s.agents.firstOrNull { it.probe.installed } ?: s.agents.firstOrNull()
        return PresetMember(role = if (count == 0) "Lead" else "Member ${count + 1}", agent = agent?.id ?: AgentId(""))
    }

    private fun fail(e: Exception) = mutable.update { it.copy(saving = false, error = e.userMessage) }
}
