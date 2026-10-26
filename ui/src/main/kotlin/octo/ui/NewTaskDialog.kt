package octo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalUriHandler
import octo.core.agent.AuthState
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
        terms != null -> TermsDialog(terms, vm)
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
        title = { Text(if (state.team) "Start a team" else "New task") },
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
        if (state.presetsEnabled) ModeChoice(state, vm)
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
        OutlinedTextField(state.baseRef, vm::editBaseRef, Modifier.fillMaxWidth(), label = {
            Text("Base branch")
        }, singleLine = true)
        if (state.team) {
            TeamChoice(state, vm, onEditPresets)
        } else {
            SingleAgent(state, vm)
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun SingleAgent(
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
    OutlinedTextField(
        state.prompt,
        vm::editPrompt,
        Modifier.fillMaxWidth(),
        label = { Text("First prompt") },
        minLines = 3,
    )
    Text("Agent", style = MaterialTheme.typography.titleSmall)
    if (state.loadingAgents) Text("Looking for installed agents…")
    state.agents.forEach { AgentChoice(it, selected = it.id == state.agent, onSelect = { vm.selectAgent(it.id) }) }
}

@Composable
private fun ModeChoice(
    state: NewTaskUiState,
    vm: NewTaskViewModel,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = !state.team, onClick = { vm.useTeam(false) })
        Text("One agent")
        RadioButton(selected = state.team, onClick = { vm.useTeam(true) })
        Text("Team preset")
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
private fun AgentChoice(
    agent: AgentInfo,
    selected: Boolean,
    onSelect: () -> Unit,
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
        RadioButton(selected = selected, onClick = onSelect, enabled = probe.installed)
        Column {
            Text(agent.displayName)
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Octo never decides this for the user: the vendor's own terms are linked and must be accepted here. */
@Composable
private fun TermsDialog(
    notice: TermsNotice,
    vm: NewTaskViewModel,
) {
    val uri = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = vm::declineTerms,
        modifier = Modifier.width(DialogWidth),
        title = { Text("${notice.agent.displayName}: vendor terms") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
                Text(notice.terms.summary)
                TextButton(onClick = { uri.openUri(notice.terms.url) }) { Text("Read the terms") }
            }
        },
        confirmButton = { Button(onClick = vm::acceptTerms) { Text("I understand, continue") } },
        dismissButton = { TextButton(onClick = vm::declineTerms) { Text("Back") } },
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
