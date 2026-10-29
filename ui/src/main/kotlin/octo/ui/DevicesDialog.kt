package octo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import octo.ui.vm.DevicesUiState
import octo.ui.vm.DevicesViewModel

@Composable
internal fun DevicesDialog(
    vm: DevicesViewModel,
    onDismiss: () -> Unit,
) {
    val state by vm.state.collectAsState()
    GlassDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.width(DialogWidth),
        title = { Text("Paired devices") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Gap)) {
                Address(state)
                Pairing(state, vm)
                DeviceList(state, vm)
                state.error?.let { Notice(it, Tone.Error) }
            }
        },
        confirmButton = {
            if (state.code == null) PrimaryButton(onClick = vm::startPairing) { Text("Pair a device") }
        },
        dismissButton = { GhostButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun Address(state: DevicesUiState) {
    val port = state.url.substringAfterLast(':')
    Text("Octo listens on ${state.url} on this computer only.")
    Text(
        "Reach it from a phone or another computer through your own tunnel, which also encrypts it:",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SelectionContainer {
        Text(
            "ssh -L $port:127.0.0.1:$port <this computer>\ntailscale serve $port",
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun Pairing(
    state: DevicesUiState,
    vm: DevicesViewModel,
) {
    val code = state.code ?: return
    Text("Enter this code on the device. It works once, for ${state.validMinutes} minutes.")
    Row(verticalAlignment = Alignment.CenterVertically) {
        SelectionContainer(Modifier.weight(1f)) {
            Text(code, style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace)
        }
        GhostButton(onClick = vm::stopPairing) { Text("Cancel") }
    }
}

@Composable
private fun DeviceList(
    state: DevicesUiState,
    vm: DevicesViewModel,
) {
    if (state.devices.isEmpty()) Text("No devices paired yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    state.devices.forEach { device ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(device.name, Modifier.weight(1f))
            GhostButton(onClick = { vm.revoke(device.id) }) { Text("Revoke") }
        }
    }
}
