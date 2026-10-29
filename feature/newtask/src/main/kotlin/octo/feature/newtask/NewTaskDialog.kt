package octo.feature.newtask

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import octo.core.agent.PermissionMode
import octo.core.task.TaskId
import octo.design.Chevron
import octo.design.Choice
import octo.design.FieldLabel
import octo.design.Gap
import octo.design.GhostButton
import octo.design.GlassDialog
import octo.design.GlassField
import octo.design.GlassMenu
import octo.design.GlassMenuItem
import octo.design.GlassSelect
import octo.design.LargeGap
import octo.design.Notice
import octo.design.PrimaryButton
import octo.design.Pyrlyn
import octo.design.SecondaryButton
import octo.design.Text
import octo.design.Tone
import octo.design.WideDialogWidth
import octo.feature.common.AgentChoice
import octo.feature.common.AttachButton
import octo.feature.common.AttachmentInput
import octo.feature.common.PendingAttachments
import octo.feature.common.TermsDialog
import octo.feature.common.agentNote
import octo.feature.common.label
import octo.feature.common.pickDirectory
import octo.feature.common.takesAttachments

@Composable
fun NewTaskDialog(
    vm: NewTaskViewModel,
    onCreated: (TaskId) -> Unit,
    onDismiss: () -> Unit,
    /** Opens the preset editor; null while the `presets` flag is off. */
    presets: (() -> PresetsViewModel?)? = null,
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(state.created) { state.created?.let(onCreated) }
    var editing by remember { mutableStateOf(false) }
    val terms = state.terms
    when {
        terms != null -> TermsDialog(terms, onAccept = vm::acceptTerms, onDecline = vm::declineTerms)
        editing && presets != null -> PresetsEditor(presets, onDone = { editing = false })
        else -> FormDialog(state, vm, onDismiss, onEditPresets = { editing = true })
    }
}

@Composable
private fun PresetsEditor(
    presets: () -> PresetsViewModel?,
    onDone: () -> Unit,
) {
    val editor = remember { presets() } ?: return
    DisposableEffect(editor) { onDispose { editor.close() } }
    PresetsDialog(editor, onDismiss = onDone)
}

@Composable
private fun FormDialog(
    state: NewTaskUiState,
    vm: NewTaskViewModel,
    onDismiss: () -> Unit,
    onEditPresets: () -> Unit,
) {
    GlassDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.width(WideDialogWidth),
        title = {
            Text(
                when {
                    state.team -> "Start a team"
                    state.compare -> "Compare agents"
                    else -> "New task"
                },
            )
        },
        text = { Form(state, vm, onEditPresets) },
        confirmButton = {
            PrimaryButton(
                onClick = vm::create,
                enabled = state.canCreate,
            ) { Text(if (state.creating) "Starting…" else "Create") }
        },
        dismissButton = { GhostButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Form(
    state: NewTaskUiState,
    vm: NewTaskViewModel,
    onEditPresets: () -> Unit,
) {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Gap)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Gap)) {
            GlassField(
                state.repo,
                vm::editRepo,
                Modifier.weight(1f),
                label = { Text(state.machine?.let { "Repository path on $it" } ?: "Repository") },
                singleLine = true,
            )
            // The folder picker only sees this computer's disk.
            if (state.machine == null) {
                SecondaryButton(onClick = { pickDirectory(state.repo)?.let(vm::editRepo) }) { Text("Browse…") }
            }
        }
        BranchChoice(state, vm)
        if (state.presetsEnabled || state.compareEnabled) ModeChoice(state, vm)
        when {
            state.team -> TeamChoice(state, vm, onEditPresets)
            state.compare -> CompareChoice(state, vm)
            else -> SingleAgent(state, vm)
        }
        if (state.machines.isNotEmpty()) MachineChoice(state, vm)
        state.error?.let { Notice(it, Tone.Error) }
    }
}

/** A select whose field doubles as a search box; whatever is typed, only a listed branch is taken. */
@Composable
private fun BranchChoice(
    state: NewTaskUiState,
    vm: NewTaskViewModel,
) {
    var open by remember { mutableStateOf(false) }
    var query by remember(state.baseRef) { mutableStateOf(state.baseRef) }
    val searching = query != state.baseRef
    val matches =
        if (searching) {
            state.branches.filter {
                it.contains(
                    query.trim(),
                    ignoreCase = true,
                )
            }
        } else {
            state.branches
        }
    // Closing without a pick puts the chosen branch back, so the field never shows one that is not used.
    val close = {
        open = false
        query = state.baseRef
    }
    val pick = { branch: String ->
        vm.selectBaseRef(branch)
        open = false
        query = branch
    }
    val note = branchNote(state, searching && matches.isEmpty())
    GlassField(
        query,
        {
            query = it
            open = true
        },
        Modifier.fillMaxWidth().onFocusChanged {
            if (!it.isFocused) {
                close()
            } else if (state.branches.isNotEmpty()) {
                open = true
            }
        },
        label = { Text("Base branch") },
        trailingIcon = { Chevron(open) },
        supportingText = note?.let { { Text(it.first) } },
        isError = note?.second == true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { matches.firstOrNull()?.let(pick) }),
        singleLine = true,
        // The keyboard stays in the field, so typing keeps narrowing the list.
        menu = { width ->
            GlassMenu(open && matches.isNotEmpty(), close, Modifier.width(width), focusable = false) {
                matches.forEach { branch -> GlassMenuItem(onClick = { pick(branch) }) { Text(branch) } }
            }
        },
    )
}

/** The hint under the branch field and whether it is an error. */
private fun branchNote(
    state: NewTaskUiState,
    noMatch: Boolean,
): Pair<String, Boolean>? =
    when {
        state.repo.isBlank() -> null
        state.loadingBranches -> "Looking for branches…" to false
        state.branchesError != null -> state.branchesError to true
        state.branches.isEmpty() -> "The repository has no branches yet" to true
        noMatch -> "No branch matches" to true
        else -> null
    }

@Composable
private fun SingleAgent(
    state: NewTaskUiState,
    vm: NewTaskViewModel,
) {
    AgentSelect(state, vm)
    TitleAndPrompt(state, vm)
    if (state.permissionModes.size > 1) PermissionChoice(state, vm)
}

@Composable
private fun CompareChoice(
    state: NewTaskUiState,
    vm: NewTaskViewModel,
) {
    TitleAndPrompt(state, vm)
    FieldLabel("Agents")
    if (state.loadingAgents) Text("Looking for installed agents…")
    state.agents.forEach { agent ->
        val models = state.compared[agent.id]
        AgentChoice(agent, selected = models != null, onSelect = { vm.toggleCompared(agent.id) }, check = true)
        if (models != null) {
            GlassField(
                models,
                { vm.editComparedModels(agent.id, it) },
                Modifier.fillMaxWidth().padding(start = LargeGap * 2),
                label = { Text("Models") },
                placeholder = { Text("Default; separate several with commas") },
                singleLine = true,
            )
        }
    }
    Text(
        "${state.picks.size} tasks, one branch each",
        style = Pyrlyn.type.small,
        color = Pyrlyn.colors.fgMuted,
    )
}

@Composable
private fun TitleAndPrompt(
    state: NewTaskUiState,
    vm: NewTaskViewModel,
) {
    GlassField(
        state.title,
        vm::editTitle,
        Modifier.fillMaxWidth(),
        label = { Text("Title") },
        placeholder = { Text(state.effectiveTitle.ifEmpty { "Taken from the prompt when empty" }) },
        singleLine = true,
    )
    val input = remember(vm) { AttachmentInput(vm::attach, vm::showError) }
    GlassField(
        state.prompt,
        vm::editPrompt,
        Modifier.fillMaxWidth().takesAttachments(input),
        label = { Text("First prompt") },
        minLines = 3,
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Gap)) {
        AttachButton(input)
        PendingAttachments(state.attachments, vm::detach)
    }
}

@Composable
private fun PermissionChoice(
    state: NewTaskUiState,
    vm: NewTaskViewModel,
) {
    FieldLabel("Permissions")
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(ChoiceSpacing),
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        (listOf<PermissionMode?>(null) + state.permissionModes).forEach { mode ->
            Choice(
                mode?.label ?: "Default from the config",
                state.chosenPermissionMode == mode,
                { vm.selectPermissionMode(mode) },
            )
        }
    }
}

@Composable
private fun ModeChoice(
    state: NewTaskUiState,
    vm: NewTaskViewModel,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(ChoiceSpacing)) {
        Choice("One agent", !state.team && !state.compare, { vm.useTeam(false) })
        if (state.presetsEnabled) Choice("Team preset", state.team, { vm.useTeam(true) })
        if (state.compareEnabled) Choice("Compare agents", state.compare, { vm.useCompare(true) })
    }
}

@Composable
private fun TeamChoice(
    state: NewTaskUiState,
    vm: NewTaskViewModel,
    onEditPresets: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FieldLabel("Preset", Modifier.weight(1f))
        GhostButton(onClick = onEditPresets) { Text("Edit presets…") }
    }
    if (state.presets.isEmpty()) Text("No presets yet.")
    state.presets.forEach { preset ->
        Choice(
            preset.name,
            preset.name == state.preset,
            { vm.selectPreset(preset.name) },
            note = preset.summary(state.agents),
        )
    }
    if (state.loadingAgents) Text("Looking for installed agents…")
    if (!state.loadingAgents && state.missingAgents.isNotEmpty()) {
        Notice("Not installed here: ${state.missingAgents.joinToString { it.value }}", Tone.Warning)
    }
}

@Composable
private fun MachineChoice(
    state: NewTaskUiState,
    vm: NewTaskViewModel,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Gap)) {
        FieldLabel("Machine")
        GlassSelect(
            listOf<String?>(null) + state.machines,
            state.machine,
            vm::selectMachine,
            { it ?: "This computer" },
            Modifier.width(MachineWidth),
        )
    }
}

/** A select rather than a list, so every agent stays reachable however short the window is. */
@Composable
private fun AgentSelect(
    state: NewTaskUiState,
    vm: NewTaskViewModel,
) {
    val selected = state.selectedAgent
    val note =
        when {
            state.loadingAgents -> "Looking for installed agents…"
            selected != null -> agentNote(selected)
            state.agents.isNotEmpty() -> "No agent is installed here"
            else -> null
        }
    GlassSelect(
        state.agents,
        selected,
        { vm.selectAgent(it.id) },
        { it.displayName },
        Modifier.fillMaxWidth(),
        label = "Agent",
        supportingText = note,
        isError = !state.loadingAgents && selected == null && state.agents.isNotEmpty(),
        optionNote = ::agentNote,
        optionEnabled = { it.probe.installed },
    )
}

private val ChoiceSpacing = 18.dp
private val MachineWidth = 220.dp
