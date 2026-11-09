// The composer's pickers in the macOS design, shared by the task screen and the New task sheet.
package octo.feature.common

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import octo.core.agent.PermissionMode
import octo.core.orchestrator.AgentInfo
import pyrlyn.macos.Mac
import pyrlyn.macos.MacIcon
import pyrlyn.macos.Menu
import pyrlyn.macos.MenuItem
import pyrlyn.macos.MenuSeparator
import pyrlyn.macos.PickerButton

/**
 * The permission mode as a shield and its name, orange when the agent runs without asking.
 * [defaultLabel] adds a first entry that leaves the choice to the config, reported as null.
 */
@Composable
fun PermissionPicker(
    current: PermissionMode?,
    modes: List<PermissionMode>,
    onSelect: (PermissionMode?) -> Unit,
    defaultLabel: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    val pick = { mode: PermissionMode? ->
        open = false
        if (mode != current) onSelect(mode)
    }
    Box {
        PickerButton(
            current?.label ?: defaultLabel.orEmpty(),
            { open = true },
            icon = MacIcon.Shield,
            tint = if (current == PermissionMode.FullAccess) Mac.colors.statusWarning else null,
        )
        Menu(open, { open = false }) {
            defaultLabel?.let {
                MenuItem(it, { pick(null) }, checked = current == null)
                MenuSeparator()
            }
            modes.forEach { mode ->
                MenuItem(
                    mode.label,
                    { pick(mode) },
                    checked = mode == current,
                    danger = mode == PermissionMode.FullAccess,
                    icon = MacIcon.Shield,
                )
            }
        }
    }
}

/** The agent as its name and version; agents that cannot run here are listed but not offered. */
@Composable
fun AgentPicker(
    agents: List<AgentInfo>,
    selected: AgentInfo?,
    onSelect: (AgentInfo) -> Unit,
    placeholder: String,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        PickerButton(
            selected?.displayName ?: placeholder,
            { open = true },
            detail = selected?.probe?.version,
            icon = MacIcon.Sparkle,
            enabled = agents.isNotEmpty(),
        )
        Menu(open, { open = false }) {
            agents.forEach { agent ->
                MenuItem(
                    agent.displayName,
                    {
                        open = false
                        onSelect(agent)
                    },
                    checked = agent == selected,
                    enabled = agent.probe.installed,
                    shortcut = agentNote(agent).takeIf { it.isNotEmpty() },
                )
            }
        }
    }
}
