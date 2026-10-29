package octo.feature.newtask

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
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
import octo.design.DialogWidth
import octo.design.Gap
import octo.design.GhostButton
import octo.design.GlassDialog
import octo.design.GlassField
import octo.design.LargeGap
import octo.design.Notice
import octo.design.PrimaryButton
import octo.design.SecondaryButton
import octo.design.Tone
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
        modifier = Modifier.width(DialogWidth),
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
        if (state.presetsEnabled || state.compareEnabled) ModeChoice(state, vm)
        if (state.machines.isNotEmpty()) MachineChoice(state, vm)
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
        when {
            state.team -> TeamChoice(state, vm, onEditPresets)
            state.compare -> CompareChoice(state, vm)
            else -> SingleAgent(state, vm)
        }
        state.error?.let { Notice(it, Tone.Error) }
    }
}

/** A select whose field doubles as a search box; whatever is typed, only a listed branch is taken. */
@OptIn(ExperimentalMaterial3Api::class)
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
    ExposedDropdownMenuBox(open, onExpandedChange = {
        if (it &&
            state.branches.isNotEmpty()
        ) {
            open = true
        } else {
            close()
        }
    }) {
        GlassField(
            query,
            {
                query = it
                open = true
            },
            Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .fillMaxWidth()
                .onFocusChanged { if (!it.isFocused) close() },
            label = { Text("Base branch") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            supportingText = note?.let { { Text(it.first) } },
            isError = note?.second == true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { matches.firstOrNull()?.let(pick) }),
            singleLine = true,
        )
        ExposedDropdownMenu(open && matches.isNotEmpty(), onDismissRequest = close) {
            matches.forEach { branch -> DropdownMenuItem(text = { Text(branch) }, onClick = { pick(branch) }) }
        }
    }
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
    TitleAndPrompt(state, vm)
    AgentSelect(state, vm)
    if (state.permissionModes.size > 1) PermissionChoice(state, vm)
}

@Composable
private fun CompareChoice(
    state: NewTaskUiState,
    vm: NewTaskViewModel,
) {
    TitleAndPrompt(state, vm)
    Text("Agents", style = MaterialTheme.typography.titleSmall)
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
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    Text("Permissions", style = MaterialTheme.typography.titleSmall)
    (listOf<PermissionMode?>(null) + state.permissionModes).forEach { mode ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RadioButton(selected = state.chosenPermissionMode == mode, onClick = { vm.selectPermissionMode(mode) })
            Text(mode?.label ?: "Default from the config")
        }
    }
}

@Composable
private fun ModeChoice(
    state: NewTaskUiState,
    vm: NewTaskViewModel,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        RadioButton(selected = !state.team && !state.compare, onClick = { vm.useTeam(false) })
        Text("One agent")
        if (state.presetsEnabled) {
            RadioButton(selected = state.team, onClick = { vm.useTeam(true) })
            Text("Team preset")
        }
        if (state.compareEnabled) {
            RadioButton(selected = state.compare, onClick = { vm.useCompare(true) })
            Text("Compare agents")
        }
    }
}

@Composable
private fun TeamChoice(
    state: NewTaskUiState,
    vm: NewTaskViewModel,
    onEditPresets: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Preset", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        GhostButton(onClick = onEditPresets) { Text("Edit presets…") }
    }
    if (state.presets.isEmpty()) Text("No presets yet.")
    state.presets.forEach { preset ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RadioButton(selected = preset.name == state.preset, onClick = { vm.selectPreset(preset.name) })
            Column {
                Text(preset.name)
                Text(
                    preset.summary(state.agents),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
    Text("Machine", style = MaterialTheme.typography.titleSmall)
    (listOf<String?>(null) + state.machines).forEach { id ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RadioButton(selected = state.machine == id, onClick = { vm.selectMachine(id) })
            Text(id ?: "This computer")
        }
    }
}

/** A select rather than a list, so every agent stays reachable however short the window is. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentSelect(
    state: NewTaskUiState,
    vm: NewTaskViewModel,
) {
    var open by remember { mutableStateOf(false) }
    val selected = state.selectedAgent
    val note =
        when {
            state.loadingAgents -> "Looking for installed agents…"
            selected != null -> agentNote(selected)
            state.agents.isNotEmpty() -> "No agent is installed here"
            else -> null
        }
    ExposedDropdownMenuBox(open, onExpandedChange = { open = it && state.agents.isNotEmpty() }) {
        GlassField(
            selected?.displayName.orEmpty(),
            {},
            Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            readOnly = true,
            label = { Text("Agent") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            supportingText = note?.let { { Text(it) } },
            isError = !state.loadingAgents && selected == null && state.agents.isNotEmpty(),
            singleLine = true,
        )
        ExposedDropdownMenu(open, onDismissRequest = { open = false }) {
            state.agents.forEach { agent ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(agent.displayName)
                            Text(
                                agentNote(agent),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        vm.selectAgent(agent.id)
                        open = false
                    },
                    // Shown anyway, so the user sees which agents Octo knows and that this one needs installing.
                    enabled = agent.probe.installed,
                )
            }
        }
    }
}
