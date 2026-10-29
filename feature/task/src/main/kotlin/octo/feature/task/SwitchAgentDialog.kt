package octo.feature.task

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import octo.design.DialogWidth
import octo.design.Gap
import octo.design.GhostButton
import octo.design.GlassDialog
import octo.design.GlassField
import octo.design.Notice
import octo.design.PrimaryButton
import octo.design.Tone
import octo.feature.common.AgentChoice
import octo.feature.common.TermsDialog
import octo.feature.common.label

@Composable
fun SwitchAgentDialog(
    vm: SwitchAgentViewModel,
    onDismiss: () -> Unit,
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(state.done) { if (state.done) onDismiss() }
    state.terms?.let {
        TermsDialog(it, onAccept = vm::acceptTerms, onDecline = vm::declineTerms)
        return
    }
    GlassDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.width(DialogWidth),
        title = { Text("Switch agent or model") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
                if (state.loading) Text("Looking for agents…")
                state.agents.forEach {
                    AgentChoice(it, selected = it.id == state.agent, onSelect = { vm.selectAgent(it.id) })
                }
                GlassField(
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
                state.error?.let { Notice(it, Tone.Error) }
            }
        },
        confirmButton = {
            PrimaryButton(onClick = vm::switch, enabled = state.canSwitch) {
                Text(if (state.switching) "Switching…" else "Switch")
            }
        },
        dismissButton = { GhostButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
