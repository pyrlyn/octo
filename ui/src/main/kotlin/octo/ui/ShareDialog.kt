package octo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import octo.ui.vm.ShareViewModel

@Composable
internal fun ShareButton(vm: ShareViewModel) {
    val state by vm.state.collectAsState()
    OutlinedButton(onClick = vm::open) { Text(if (state.shared) "Shared" else "Share") }
}

/** What the guest asked to send, and the share dialog; nothing reaches the agent until the owner sends it here. */
@Composable
internal fun GuestProposals(vm: ShareViewModel) {
    val state by vm.state.collectAsState()
    ShareDialog(vm)
    if (!state.open) state.error?.let { Banner(it, error = true, onDismiss = vm::dismissError) }
    state.waiting.forEach { proposal ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Gap)) {
            Column(Modifier.weight(1f)) {
                Text("${proposal.from} proposes:", fontWeight = FontWeight.SemiBold)
                Text(proposal.text)
            }
            Button(onClick = { vm.approve(proposal.id) }) { Text("Send") }
            TextButton(onClick = { vm.reject(proposal.id) }) { Text("Reject") }
        }
    }
}

@Composable
private fun ShareDialog(vm: ShareViewModel) {
    val state by vm.state.collectAsState()
    if (!state.open) return
    AlertDialog(
        onDismissRequest = vm::dismiss,
        modifier = Modifier.width(DialogWidth),
        title = { Text("Share with a guest") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
                Text(
                    "A guest with the link watches this task and proposes messages; " +
                        "a message reaches the agent only once you send it.",
                    style = MaterialTheme.typography.bodySmall,
                )
                state.link?.let {
                    Text("Copy the link now; it is not shown again.", fontWeight = FontWeight.SemiBold)
                    SelectionContainer { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                if (state.shared && state.link == null) {
                    Text("Shared. A new link ends the old one.", style = MaterialTheme.typography.bodySmall)
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = vm::create) { Text(if (state.shared) "New link" else "Create link") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
                if (state.shared) TextButton(onClick = vm::revoke) { Text("Revoke") }
                TextButton(onClick = vm::dismiss) { Text("Close") }
            }
        },
    )
}
