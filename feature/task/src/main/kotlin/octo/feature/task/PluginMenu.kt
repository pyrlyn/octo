package octo.feature.task

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import octo.design.Banner
import octo.design.GhostButton
import octo.design.GlassDialog
import octo.design.GlassMenu
import octo.design.GlassMenuItem
import octo.design.SecondaryButton
import octo.design.Text

/** The Plugins button of the task header, and what an action shows. */
@Composable
internal fun PluginMenu(vm: PluginActionsViewModel) {
    val state by vm.state.collectAsState()
    var open by remember { mutableStateOf(false) }
    Box {
        SecondaryButton(onClick = { open = true }, enabled = !state.running) {
            Text(if (state.running) "Running…" else "Plugins")
        }
        GlassMenu(expanded = open, onDismissRequest = { open = false }) {
            state.actions.forEach { item ->
                GlassMenuItem(onClick = {
                    open = false
                    vm.run(item)
                }) { Text(item.title) }
            }
        }
    }
    state.message?.let { message ->
        GlassDialog(
            onDismissRequest = vm::dismissMessage,
            title = { Text(message.plugin) },
            text = { SelectionContainer { Text(message.text) } },
            confirmButton = { GhostButton(onClick = vm::dismissMessage) { Text("OK") } },
        )
    }
    state.error?.let { Banner(it, error = true, onDismiss = vm::dismissError) }
}
