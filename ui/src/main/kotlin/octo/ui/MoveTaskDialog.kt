package octo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import octo.ui.vm.MoveTaskViewModel

@Composable
internal fun MoveTaskDialog(
    vm: MoveTaskViewModel,
    onDismiss: () -> Unit,
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(state.done) { if (state.done) onDismiss() }
    AlertDialog(
        onDismissRequest = { if (!state.moving) onDismiss() },
        modifier = Modifier.width(DialogWidth),
        title = { Text("Move to another machine") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
                if (state.targets.isEmpty()) {
                    Text("No other machine has a clone of this repository; list it under that machine's repos.")
                }
                state.targets.forEach { id ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = state.chosen && state.target == id, onClick = { vm.select(id) })
                        Text(id ?: "This computer")
                    }
                }
                Text(
                    "The branch and its uncommitted changes go to a new worktree there, and this one is deleted. " +
                        "A fresh session starts; your next prompt carries the conversation over.",
                    style = MaterialTheme.typography.bodySmall,
                )
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = vm::move, enabled = state.canMove) { Text(if (state.moving) "Moving…" else "Move") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.moving) { Text("Cancel") } },
    )
}
