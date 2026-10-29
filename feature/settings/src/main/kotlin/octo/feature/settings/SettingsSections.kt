package octo.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import octo.core.agent.AuthState
import octo.core.config.MachineConfig
import octo.core.orchestrator.AgentInfo
import octo.design.Dot
import octo.design.Gap
import octo.design.GhostButton
import octo.design.GlassDialog
import octo.design.GlassField
import octo.design.Hairline
import octo.design.Notice
import octo.design.PrimaryButton
import octo.design.Pyrlyn
import octo.design.SecondaryButton
import octo.design.SettingRow
import octo.design.SettingsGroup
import octo.design.Text
import octo.design.Tone
import octo.feature.common.authColor
import octo.feature.common.label
import octo.feature.common.pickDirectory

@Composable
internal fun AgentsSection(
    state: SettingsUiState,
    vm: SettingsViewModel,
) {
    SettingsGroup("Found on this computer") {
        val agents = state.agents
        when {
            agents == null -> {
                SettingRow("Looking for agents…")
            }

            agents.isEmpty() -> {
                SettingRow("No agents are registered in this build")
            }

            else -> {
                agents.forEachIndexed { i, agent ->
                    if (i > 0) Hairline()
                    AgentSetting(agent)
                }
            }
        }
    }
    GhostButton(onClick = vm::refreshAgents) { Text("Look again") }
    FlagGroup("Adapters (flags)", vm.flags(SettingsSection.Agents), state, vm)
    WorktreeSetup(state, vm)
}

@Composable
private fun AgentSetting(agent: AgentInfo) {
    val probe = agent.probe
    val (word, color) =
        when {
            !probe.installed -> "Not installed" to Pyrlyn.colors.fgSubtle
            probe.auth == AuthState.LoggedOut -> "Set up" to authColor(probe.auth)
            else -> "Ready" to authColor(probe.auth)
        }
    SettingRow(agent.displayName, listOfNotNull(probe.version, probe.binaryPath).joinToString(" · ").ifEmpty { null }) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Dot(color)
            Text(word, style = Pyrlyn.type.small, color = Pyrlyn.colors.fgMuted)
        }
    }
}

@Composable
private fun WorktreeSetup(
    state: SettingsUiState,
    vm: SettingsViewModel,
) {
    // The repository being edited; "" for a new one.
    var editing by remember { mutableStateOf<String?>(null) }
    val setup = state.config.worktreeSetup
    SettingsGroup("Worktree setup") {
        if (setup.isEmpty()) {
            SettingRow(
                "No setup commands",
                "A command here runs in every new worktree of its repository",
            )
        }
        setup.entries.forEachIndexed { i, (repo, command) ->
            if (i > 0) Hairline()
            SettingRow(displayPath(repo), mono = command) {
                GhostButton(onClick = { editing = repo }) { Text("Edit") }
                GhostButton(onClick = { vm.removeWorktreeSetup(repo) }) { Text("Remove") }
            }
        }
    }
    GhostButton(onClick = { editing = "" }) { Text("+  Add repository") }
    editing?.let { repo ->
        WorktreeSetupDialog(repo, setup[repo].orEmpty(), { editing = null }) { path, command ->
            vm.saveWorktreeSetup(path, command, repo.ifEmpty { null })
        }
    }
}

@Composable
private fun WorktreeSetupDialog(
    repo: String,
    command: String,
    onDismiss: () -> Unit,
    /** Why the values cannot be saved, or null once they are. */
    onSave: (String, String) -> String?,
) {
    var path by remember { mutableStateOf(repo) }
    var text by remember { mutableStateOf(command) }
    var error by remember { mutableStateOf<String?>(null) }
    GlassDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (repo.isEmpty()) "Add worktree setup" else "Edit worktree setup") },
        text = {
            Column(Modifier.width(DialogFieldWidth), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassField(
                    path,
                    { path = it },
                    singleLine = true,
                    label = { Text("Repository") },
                    placeholder = { Text("/Users/you/src/repo") },
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    trailingIcon = {
                        GhostButton(
                            onClick = { pickDirectory(path)?.let { path = it } },
                        ) { Text("Browse…") }
                    },
                )
                GlassField(
                    text,
                    { text = it },
                    label = { Text("Command") },
                    placeholder = { Text("npm ci") },
                    supportingText = { Text("Runs with /bin/sh in the new worktree before the agent starts") },
                    minLines = 2,
                )
            }
        },
        dismissButton = { GhostButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            PrimaryButton(onClick = { onSave(path, text).also { error = it } ?: onDismiss() }) { Text("Save") }
        },
    )
}

@Composable
internal fun DevicesSection(
    state: SettingsUiState,
    vm: SettingsViewModel,
    onPair: (() -> Unit)?,
) {
    FlagGroup("Features (flags)", vm.flags(SettingsSection.Devices), state, vm)
    SettingsGroup("Remote access") {
        PortSetting(state.config.remoteAccess.port, vm)
        Hairline()
        SettingRow(
            "Paired devices",
            if (onPair == null) "Turn on remote-access and restart Octo to pair a phone or browser" else null,
        ) {
            if (onPair != null) SecondaryButton(onClick = onPair) { Text("Pair a device") }
        }
    }
    Machines(state, vm)
}

@Composable
private fun PortSetting(
    port: Int,
    vm: SettingsViewModel,
) {
    var text by remember(port) { mutableStateOf(port.toString()) }
    var error by remember(port) { mutableStateOf<String?>(null) }
    SettingRow("Port", "Loopback only; point your tunnel at it", error = error) {
        GlassField(text, { text = it }, Modifier.width(PortWidth), singleLine = true, isError = error != null)
        GhostButton(onClick = { error = vm.savePort(text) }, enabled = text != port.toString()) { Text("Save") }
    }
}

@Composable
private fun Machines(
    state: SettingsUiState,
    vm: SettingsViewModel,
) {
    // The machine being edited; an empty id for a new one.
    var editing by remember { mutableStateOf<MachineConfig?>(null) }
    val machines = state.config.machines
    SettingsGroup("Machines (ssh)") {
        if (machines.isEmpty()) {
            SettingRow(
                "No machines",
                "Agents on a machine run over ssh with the remote-ssh flag on",
            )
        }
        machines.forEachIndexed { i, machine ->
            if (i > 0) Hairline()
            SettingRow(machine.id, mono = machine.address, error = machine.problem()) {
                GhostButton(onClick = { editing = machine }) { Text("Edit") }
                GhostButton(onClick = { vm.removeMachine(machine.id) }) { Text("Remove") }
            }
        }
    }
    GhostButton(onClick = { editing = MachineConfig("", "") }) { Text("+  Add machine") }
    editing?.let { machine ->
        MachineDialog(machine, { editing = null }) { vm.saveMachine(machine.id.ifEmpty { null }, it) }
    }
}

private val MachineConfig.address: String
    get() = listOfNotNull(host + (port?.let { ":$it" } ?: ""), worktrees).joinToString("  ")

@Composable
private fun MachineDialog(
    machine: MachineConfig,
    onDismiss: () -> Unit,
    /** Why the machine cannot be saved, or null once it is. */
    onSave: (MachineConfig) -> String?,
) {
    var id by remember { mutableStateOf(machine.id) }
    var host by remember { mutableStateOf(machine.host) }
    var port by remember { mutableStateOf(machine.port?.toString().orEmpty()) }
    var worktrees by remember { mutableStateOf(machine.worktrees.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    GlassDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (machine.id.isEmpty()) "Add machine" else "Edit ${machine.id}") },
        text = {
            Column(Modifier.width(DialogFieldWidth), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassField(id, { id = it }, singleLine = true, label = { Text("Id") }, placeholder = { Text("devbox") })
                Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
                    GlassField(
                        host,
                        { host = it },
                        Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Host") },
                        placeholder = { Text("user@devbox.local") },
                    )
                    GlassField(
                        port,
                        { port = it },
                        Modifier.width(PortWidth),
                        singleLine = true,
                        label = { Text("Port") },
                        placeholder = { Text("22") },
                    )
                }
                GlassField(
                    worktrees,
                    { worktrees = it },
                    singleLine = true,
                    label = { Text("Worktrees") },
                    placeholder = { Text("\$HOME/.octo/worktrees") },
                )
                error?.let { Notice(it, Tone.Error) }
            }
        },
        dismissButton = { GhostButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            PrimaryButton(onClick = {
                val number = port.trim().ifEmpty { null }?.let { it.toIntOrNull() ?: -1 }
                val edited =
                    machine.copy(
                        id = id.trim(),
                        host = host.trim(),
                        port = number,
                        worktrees = worktrees.trim().ifEmpty { null },
                    )
                error = onSave(edited)
                if (error == null) onDismiss()
            }) { Text("Save") }
        },
    )
}

private val DialogFieldWidth = 420.dp
private val PortWidth = 96.dp
