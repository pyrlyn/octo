package octo.ui

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import octo.core.agent.AuthState
import octo.core.agent.PermissionMode
import octo.core.orchestrator.AgentInfo
import octo.core.task.TaskId
import octo.ui.vm.NewTaskUiState
import octo.ui.vm.NewTaskViewModel
import octo.ui.vm.PresetsViewModel
import octo.ui.vm.TermsNotice
import javax.swing.JFileChooser

@Composable
internal fun NewTaskDialog(
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
    AlertDialog(
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
            Button(
                onClick = vm::create,
                enabled = state.canCreate,
            ) { Text(if (state.creating) "Starting…" else "Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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
            OutlinedTextField(
                state.repo,
                vm::editRepo,
                Modifier.weight(1f),
                label = { Text(state.machine?.let { "Repository path on $it" } ?: "Repository") },
                singleLine = true,
            )
            // The folder picker only sees this computer's disk.
            if (state.machine == null) {
                OutlinedButton(onClick = { pickDirectory(state.repo)?.let(vm::editRepo) }) { Text("Browse…") }
            }
        }
        BranchChoice(state, vm)
        when {
            state.team -> TeamChoice(state, vm, onEditPresets)
            state.compare -> CompareChoice(state, vm)
            else -> SingleAgent(state, vm)
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
        OutlinedTextField(
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
    Text("Agent", style = MaterialTheme.typography.titleSmall)
    if (state.loadingAgents) Text("Looking for installed agents…")
    state.agents.forEach { AgentChoice(it, selected = it.id == state.agent, onSelect = { vm.selectAgent(it.id) }) }
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
            OutlinedTextField(
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
    OutlinedTextField(
        state.title,
        vm::editTitle,
        Modifier.fillMaxWidth(),
        label = { Text("Title") },
        placeholder = { Text(state.effectiveTitle.ifEmpty { "Taken from the prompt when empty" }) },
        singleLine = true,
    )
    if (!state.canAttach) {
        OutlinedTextField(
            state.prompt,
            vm::editPrompt,
            Modifier.fillMaxWidth(),
            label = { Text("First prompt") },
            minLines = 3,
        )
        return
    }
    Column(Modifier.dropFiles(vm.files), verticalArrangement = Arrangement.spacedBy(Gap)) {
        OutlinedTextField(
            state.prompt,
            vm::editPrompt,
            Modifier.fillMaxWidth().onPreviewKeyEvent { pasteFiles(it, vm.files) },
            label = { Text("First prompt") },
            minLines = 3,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Gap)) {
            AttachButton(vm.files)
            PickedFiles(state.files, vm.files)
        }
    }
}

@Composable
private fun PermissionChoice(
    state: NewTaskUiState,
    vm: NewTaskViewModel,
) {
    Text("Permissions", style = MaterialTheme.typography.titleSmall)
    (listOf<PermissionMode?>(null) + state.permissionModes).forEach { mode ->
        Row(verticalAlignment = Alignment.CenterVertically) {
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
    Row(verticalAlignment = Alignment.CenterVertically) {
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
        TextButton(onClick = onEditPresets) { Text("Edit presets…") }
    }
    if (state.presets.isEmpty()) Text("No presets yet.")
    state.presets.forEach { preset ->
        Row(verticalAlignment = Alignment.CenterVertically) {
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
        Text(
            "Not installed here: ${state.missingAgents.joinToString { it.value }}",
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun MachineChoice(
    state: NewTaskUiState,
    vm: NewTaskViewModel,
) {
    Text("Machine", style = MaterialTheme.typography.titleSmall)
    (listOf<String?>(null) + state.machines).forEach { id ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = state.machine == id, onClick = { vm.selectMachine(id) })
            Text(id ?: "This computer")
        }
    }
}

@Composable
internal fun AgentChoice(
    agent: AgentInfo,
    selected: Boolean,
    onSelect: () -> Unit,
    /** A checkbox, for picking several agents, instead of a radio button. */
    check: Boolean = false,
) {
    val probe = agent.probe
    val note =
        when {
            !probe.installed -> "not installed"
            probe.auth == AuthState.LoggedOut -> "${probe.version.orEmpty()} · log in with its own CLI first"
            probe.versionDrift -> "${probe.version} · tested with ${probe.verifiedVersion}"
            else -> probe.version.orEmpty()
        }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (check) {
            Checkbox(checked = selected, onCheckedChange = { onSelect() }, enabled = probe.installed)
        } else {
            RadioButton(selected = selected, onClick = onSelect, enabled = probe.installed)
        }
        Column {
            Text(agent.displayName)
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Octo never decides this for the user: the vendor's own terms are linked and must be accepted here. */
@Composable
internal fun TermsDialog(
    notice: TermsNotice,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    val uri = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDecline,
        modifier = Modifier.width(DialogWidth),
        title = { Text("${notice.agent.displayName}: vendor terms") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
                Text(notice.terms.summary)
                TextButton(onClick = { uri.openUri(notice.terms.url) }) { Text("Read the terms") }
            }
        },
        confirmButton = { Button(onClick = onAccept) { Text("I understand, continue") } },
        dismissButton = { TextButton(onClick = onDecline) { Text("Back") } },
    )
}

private fun pickDirectory(start: String): String? {
    val chooser =
        JFileChooser(start.ifBlank { System.getProperty("user.home") }).apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = "Choose a repository"
        }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile?.path else null
}
