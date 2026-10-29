package octo.feature.task

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import octo.design.Choice
import octo.design.DialogWidth
import octo.design.Gap
import octo.design.GhostButton
import octo.design.GlassDialog
import octo.design.Notice
import octo.design.PrimaryButton
import octo.design.Pyrlyn
import octo.design.Text
import octo.design.Tone

@Composable
fun MoveTaskDialog(
    vm: MoveTaskViewModel,
    onDismiss: () -> Unit,
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(state.done) { if (state.done) onDismiss() }
    GlassDialog(
        onDismissRequest = { if (!state.moving) onDismiss() },
        modifier = Modifier.width(DialogWidth),
        title = { Text("Move to another machine") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
                if (state.targets.isEmpty()) {
                    Text("No other machine has a clone of this repository; list it under that machine's repos.")
                }
                state.targets.forEach { id ->
                    Choice(id ?: "This computer", state.chosen && state.target == id, { vm.select(id) })
                }
                Text(
                    "The branch and its uncommitted changes go to a new worktree there, and this one is deleted. " +
                        "A fresh session starts; your next prompt carries the conversation over.",
                    style = Pyrlyn.type.small,
                )
                state.error?.let { Notice(it, Tone.Error) }
            }
        },
        confirmButton = {
            PrimaryButton(onClick = vm::move, enabled = state.canMove) { Text(if (state.moving) "Moving…" else "Move") }
        },
        dismissButton = { GhostButton(onClick = onDismiss, enabled = !state.moving) { Text("Cancel") } },
    )
}
