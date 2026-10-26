package octo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import octo.ui.vm.PluginActionsViewModel

/** The Plugins button of the task header, and what an action shows. */
@Composable
internal fun PluginMenu(vm: PluginActionsViewModel) {
    val state by vm.state.collectAsState()
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, enabled = !state.running) {
            Text(if (state.running) "Running…" else "Plugins")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            state.actions.forEach { item ->
                DropdownMenuItem(text = { Text(item.title) }, onClick = {
                    open = false
                    vm.run(item)
                })
            }
        }
    }
    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = vm::dismissMessage,
            title = { Text(message.plugin) },
            text = { SelectionContainer { Text(message.text) } },
            confirmButton = { TextButton(onClick = vm::dismissMessage) { Text("OK") } },
        )
    }
    state.error?.let { Banner(it, error = true, onDismiss = vm::dismissError) }
}
