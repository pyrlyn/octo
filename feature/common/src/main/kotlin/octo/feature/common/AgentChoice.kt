package octo.feature.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import octo.core.agent.AuthState
import octo.core.orchestrator.AgentInfo
import octo.design.Pyrlyn

@Composable
fun AgentChoice(
    agent: AgentInfo,
    selected: Boolean,
    onSelect: () -> Unit,
    /** A checkbox, for picking several agents, instead of a radio button. */
    check: Boolean = false,
) {
    val installed = agent.probe.installed
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (check) {
            Checkbox(checked = selected, onCheckedChange = { onSelect() }, enabled = installed)
        } else {
            RadioButton(selected = selected, onClick = onSelect, enabled = installed)
        }
        Column {
            Text(agent.displayName)
            Text(
                agentNote(agent),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The version, or why the agent cannot run yet. */
fun agentNote(agent: AgentInfo): String {
    val probe = agent.probe
    return when {
        !probe.installed -> "not installed"
        probe.auth == AuthState.LoggedOut -> "${probe.version.orEmpty()} · log in with its own CLI first"
        probe.versionDrift -> "${probe.version} · tested with ${probe.verifiedVersion}"
        else -> probe.version.orEmpty()
    }
}

@Composable
fun authColor(auth: AuthState?) =
    when (auth) {
        AuthState.LoggedIn -> Pyrlyn.colors.success
        AuthState.LoggedOut -> Pyrlyn.colors.warning
        AuthState.Unknown, null -> Pyrlyn.colors.fgSubtle
    }
