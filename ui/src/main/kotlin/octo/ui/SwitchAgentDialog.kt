package octo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import octo.ui.vm.SwitchAgentViewModel

@Composable
internal fun SwitchAgentDialog(
    vm: SwitchAgentViewModel,
    onDismiss: () -> Unit,
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(state.done) { if (state.done) onDismiss() }
    state.terms?.let {
        TermsDialog(it, onAccept = vm::acceptTerms, onDecline = vm::declineTerms)
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.width(DialogWidth),
        title = { Text("Switch agent or model") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
                if (state.loading) Text("Looking for agents…")
                state.agents.forEach {
                    AgentChoice(it, selected = it.id == state.agent, onSelect = { vm.selectAgent(it.id) })
                }
                OutlinedTextField(
                    state.model,
                    vm::editModel,
                    Modifier.fillMaxWidth(),
                    label = { Text("Model") },
                    placeholder = { Text("The agent's default") },
                    singleLine = true,
                )
                Text(
                    if (state.handsOver) {
                        "A fresh session starts in the same worktree; your next prompt carries the conversation over."
                    } else {
                        "The running session switches when the agent can; otherwise a fresh one takes over."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = vm::switch, enabled = state.canSwitch) {
                Text(if (state.switching) "Switching…" else "Switch")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
