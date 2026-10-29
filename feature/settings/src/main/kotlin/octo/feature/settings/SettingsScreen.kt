package octo.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import octo.core.agent.PermissionMode
import octo.core.config.AppearanceTheme
import octo.core.config.RowDensity
import octo.core.config.Transparency
import octo.core.flags.FeatureFlag
import octo.design.Choice
import octo.design.DangerButton
import octo.design.Gap
import octo.design.GhostButton
import octo.design.GlassDialog
import octo.design.GlassSwitch
import octo.design.Hairline
import octo.design.LargeGap
import octo.design.Notice
import octo.design.PaneHeader
import octo.design.PanePadding
import octo.design.Pyrlyn
import octo.design.SecondaryButton
import octo.design.Segmented
import octo.design.SettingRow
import octo.design.SettingsGroup
import octo.design.Text
import octo.design.Tone
import octo.feature.common.description
import octo.feature.common.label

/**
 * Settings over `config.json`, one section at a time. [onPair] opens the devices dialog; null
 * while remote access is off.
 */
@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    onClose: () -> Unit,
    onPair: (() -> Unit)?,
) {
    val state by vm.state.collectAsState()
    Column(Modifier.fillMaxSize()) {
        PaneHeader("Settings", displayPath(vm.path)) {
            GhostButton(onClick = vm::openConfig) { Text("↗  Open config.json") }
            GhostButton(onClick = onClose) { Text("Close") }
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Nav(state.section, vm::select)
            Box(Modifier.width(0.5.dp).fillMaxHeight().background(Pyrlyn.colors.glassEdge))
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(PanePadding),
                verticalArrangement = Arrangement.spacedBy(LargeGap),
            ) {
                Notices(state, vm)
                when (state.section) {
                    SettingsSection.General -> General(state, vm)
                    SettingsSection.Agents -> AgentsSection(state, vm)
                    SettingsSection.Permissions -> Permissions(state, vm)
                    SettingsSection.Devices -> DevicesSection(state, vm, onPair)
                    SettingsSection.Appearance -> Appearance(state, vm)
                    SettingsSection.Advanced -> Advanced(state, vm)
                }
            }
        }
    }
}

@Composable
private fun Nav(
    current: SettingsSection,
    onSelect: (SettingsSection) -> Unit,
) {
    val c = Pyrlyn.colors
    val shape = Pyrlyn.controlShape
    Column(Modifier.width(NavWidth).padding(Gap), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SettingsSection.entries.forEach { section ->
            val selected = section == current
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(if (selected) c.stateSelected else c.stateSelected.copy(alpha = 0f))
                    .border(1.dp, if (selected) c.stateSelectedRim else c.stateSelectedRim.copy(alpha = 0f), shape)
                    .clickable { onSelect(section) }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(section.glyph, color = if (selected) c.stateSelectedFg else c.fgMuted)
                Text(
                    section.title,
                    color = if (selected) c.stateSelectedFg else c.fg,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

private val SettingsSection.glyph: String
    get() =
        when (this) {
            SettingsSection.General -> "⚙"
            SettingsSection.Agents -> "◎"
            SettingsSection.Permissions -> "◐"
            SettingsSection.Devices -> "⇄"
            SettingsSection.Appearance -> "◑"
            SettingsSection.Advanced -> "⋯"
        }

@Composable
private fun Notices(
    state: SettingsUiState,
    vm: SettingsViewModel,
) {
    state.snapshot?.warning?.let {
        Notice("${firstClause(it)}. Using defaults until it's fixed.", Tone.Error) {
            GhostButton(onClick = vm::openConfig) { Text("Open file") }
        }
    }
    state.error?.let { Notice(it, Tone.Error) { GhostButton(onClick = vm::dismissError) { Text("Dismiss") } } }
    if (state.restartNeeded) Notice("Saved. Restart Octo to apply these changes.", Tone.Info)
}

@Composable
private fun General(
    state: SettingsUiState,
    vm: SettingsViewModel,
) {
    SettingsGroup("Location") {
        SettingRow("Octo home", "OCTO_HOME, or the platform's app data folder", mono = displayPath(vm.home)) {
            GhostButton(onClick = vm::revealHome) { Text("Reveal") }
        }
    }
    FlagGroup("Features (flags)", vm.flags(SettingsSection.General), state, vm)
}

/** A group of flag switches; nothing at all when [flags] is empty. */
@Composable
internal fun FlagGroup(
    label: String,
    flags: List<FeatureFlag>,
    state: SettingsUiState,
    vm: SettingsViewModel,
) {
    if (flags.isEmpty()) return
    val enabled = state.config.featureFlags()
    SettingsGroup(label) {
        flags.forEachIndexed { i, flag ->
            if (i > 0) Hairline()
            SettingRow(flag.id, flag.description) {
                GlassSwitch(enabled.isEnabled(flag), { vm.setFlag(flag, it) }, enabled = state.editable)
            }
        }
    }
}

@Composable
private fun Permissions(
    state: SettingsUiState,
    vm: SettingsViewModel,
) {
    SettingsGroup("Default for new tasks") {
        PermissionMode.entries.forEachIndexed { i, mode ->
            if (i > 0) Hairline()
            Choice(
                mode.label,
                state.config.defaultPermissionMode == mode,
                { vm.setPermissionMode(mode) },
                Modifier.fillMaxWidth(),
                note = mode.description,
                contentPadding = PaddingValues(horizontal = LargeGap, vertical = 9.dp),
            )
        }
    }
    Text(
        "Agents without the chosen mode run Supervised and say so in the transcript.",
        style = Pyrlyn.type.small,
        color = Pyrlyn.colors.fgMuted,
    )
}

@Composable
private fun Appearance(
    state: SettingsUiState,
    vm: SettingsViewModel,
) {
    val look = state.config.appearance
    SettingsGroup("Theme") {
        SettingRow("Color theme") {
            Segmented(AppearanceTheme.entries, look.theme, { it.label }) { vm.setAppearance(look.copy(theme = it)) }
        }
    }
    SettingsGroup("Material") {
        SettingRow("Transparency", "Opaque surfaces read better over a busy desktop or on a slow GPU") {
            Segmented(Transparency.entries, look.transparency, { it.label }) {
                vm.setAppearance(look.copy(transparency = it))
            }
        }
    }
    SettingsGroup("Density") {
        SettingRow("Rows and controls", "Compact and Comfortable take 4dp off or add 4dp to this system's sizes") {
            Segmented(RowDensity.entries, look.density, { it.label }) { vm.setAppearance(look.copy(density = it)) }
        }
    }
}

@Composable
private fun Advanced(
    state: SettingsUiState,
    vm: SettingsViewModel,
) {
    var confirmReset by remember { mutableStateOf(false) }
    FlagGroup("Experimental", vm.flags(SettingsSection.Advanced), state, vm)
    SettingsGroup("config.json") {
        val text = state.snapshot?.text
        SelectionContainer {
            Text(
                text?.trimEnd() ?: "No config.json yet: every setting is at its default.",
                Modifier
                    .fillMaxWidth()
                    .padding(LargeGap)
                    .background(Pyrlyn.colors.bgSunken, RoundedCornerShape(Pyrlyn.platform.radiusControl))
                    .padding(12.dp),
                style = Pyrlyn.type.mono,
                color = if (text == null) Pyrlyn.colors.fgMuted else Pyrlyn.colors.fg,
            )
        }
        Row(
            Modifier.padding(start = LargeGap, end = LargeGap, bottom = LargeGap),
            horizontalArrangement = Arrangement.spacedBy(Gap),
        ) {
            SecondaryButton(onClick = vm::openConfig) { Text("↗  Open in editor") }
            DangerButton(onClick = { confirmReset = true }, enabled = text != null) { Text("Reset to defaults") }
        }
    }
    if (confirmReset) {
        GlassDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset every setting?") },
            text = {
                Text(
                    "config.json is emptied: flags, machines, worktree setup and appearance go back to their defaults.",
                )
            },
            dismissButton = { GhostButton(onClick = { confirmReset = false }) { Text("Cancel") } },
            confirmButton = {
                DangerButton(onClick = {
                    confirmReset = false
                    vm.reset()
                }) { Text("Reset") }
            },
        )
    }
}

/** The parser's own words up to its second clause; the rest is a dump of the JSON it choked on. */
private fun firstClause(warning: String): String {
    val line = warning.lineSequence().first()
    val cut = line.indexOf(": ", line.indexOf(": ") + 2)
    return if (cut > 0) line.substring(0, cut) else line
}

/** [path] with the home folder shortened to `~`, as people read paths. */
internal fun displayPath(path: String): String {
    val home = System.getProperty("user.home").orEmpty()
    return if (home.isNotEmpty() && path.startsWith(home)) "~" + path.removePrefix(home) else path
}

private val NavWidth = 200.dp
