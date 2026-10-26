package octo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import octo.core.agent.AgentId
import octo.core.orchestrator.AgentInfo
import octo.core.preset.Preset
import octo.core.preset.PresetMember
import octo.ui.vm.PresetDraft
import octo.ui.vm.PresetsUiState
import octo.ui.vm.PresetsViewModel

/** The preset editor; [onDismiss] goes back to the form that opened it. */
@Composable
internal fun PresetsDialog(
    vm: PresetsViewModel,
    onDismiss: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val draft = state.draft
    AlertDialog(
        onDismissRequest = if (draft == null) onDismiss else vm::cancel,
        modifier = Modifier.width(DialogWidth),
        title = { Text(if (draft == null) "Team presets" else draft.original?.let { "Edit \"$it\"" } ?: "New preset") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Gap)) {
                if (draft == null) PresetList(state, vm) else DraftForm(draft, state.agents, vm)
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (draft == null) {
                Button(onClick = vm::create) { Text("New preset") }
            } else {
                Button(onClick = vm::save, enabled = state.canSave) { Text(if (state.saving) "Saving…" else "Save") }
            }
        },
        dismissButton = {
            TextButton(
                onClick =
                    if (draft ==
                        null
                    ) {
                        onDismiss
                    } else {
                        vm::cancel
                    },
            ) { Text(if (draft == null) "Done" else "Cancel") }
        },
    )
}

@Composable
private fun PresetList(
    state: PresetsUiState,
    vm: PresetsViewModel,
) {
    if (state.presets.isEmpty()) {
        Text(
            "No presets yet. A preset starts several agents on one repository, each with a role.",
        )
    }
    state.presets.forEach { preset ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(preset.name)
                Text(
                    preset.summary(state.agents),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { vm.edit(preset.name) }) { Text("Edit") }
            TextButton(onClick = { vm.delete(preset.name) }) { Text("Delete") }
        }
    }
}

@Composable
private fun DraftForm(
    draft: PresetDraft,
    agents: List<AgentInfo>,
    vm: PresetsViewModel,
) {
    OutlinedTextField(draft.name, vm::editName, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
    draft.members.forEachIndexed { i, member ->
        HorizontalDivider()
        MemberForm(i, member, agents, vm, canRemove = draft.members.size > 1)
    }
    OutlinedButton(onClick = vm::addMember) { Text("Add member") }
    draft.preset.problem()?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MemberForm(
    index: Int,
    member: PresetMember,
    agents: List<AgentInfo>,
    vm: PresetsViewModel,
    canRemove: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Gap)) {
        OutlinedTextField(
            member.role,
            { vm.editRole(index, it) },
            Modifier.weight(1f),
            label = { Text("Role") },
            singleLine = true,
        )
        AgentPicker(member.agent, agents) { vm.selectAgent(index, it) }
        if (canRemove) TextButton(onClick = { vm.removeMember(index) }) { Text("Remove") }
    }
    OutlinedTextField(
        member.prompt,
        { vm.editPrompt(index, it) },
        Modifier.fillMaxWidth(),
        label = { Text("First prompt") },
        minLines = 2,
    )
}

@Composable
private fun AgentPicker(
    selected: AgentId,
    agents: List<AgentInfo>,
    onSelect: (AgentId) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text(agents.nameOf(selected)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            agents.forEach { agent ->
                DropdownMenuItem(
                    text = {
                        Text(
                            if (agent.probe.installed) agent.displayName else "${agent.displayName} (not installed)",
                        )
                    },
                    onClick = {
                        onSelect(agent.id)
                        open = false
                    },
                )
            }
        }
    }
}

private fun List<AgentInfo>.nameOf(id: AgentId) =
    firstOrNull { it.id == id }?.displayName ?: id.value.ifEmpty { "Agent" }

internal fun Preset.summary(agents: List<AgentInfo>) =
    members.joinToString {
        "${it.role} · ${agents.nameOf(it.agent)}"
    }
