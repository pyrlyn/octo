package octo.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Pyrlyn's switch: 34×20, accent when on. */
@Composable
fun GlassSwitch(
    on: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = Pyrlyn.colors
    val track =
        when {
            !enabled -> c.bgSunken
            on -> c.accentSolid
            else -> c.bgActive
        }
    Box(
        modifier
            .size(SwitchWidth, SwitchHeight)
            .clip(CircleShape)
            .background(track)
            .border(1.dp, if (on) track else c.borderStrong, CircleShape)
            .toggleable(on, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(2.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        // White in both themes, as in the design: the dark floating colour vanishes on the dark track.
        Box(Modifier.size(ThumbSize).clip(CircleShape).background(if (enabled) Color.White else c.fgDisabled))
    }
}

/** A choice of a few values, the chosen one highlighted. */
@Composable
fun <T> Segmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEach { option ->
            GhostButton(onClick = { onSelect(option) }, selected = option == selected) { Text(label(option)) }
        }
    }
}

/** A labelled card of settings rows, with hairlines between them. */
@Composable
fun SettingsGroup(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Pyrlyn.colors
    val shape = RoundedCornerShape(minOf(Pyrlyn.platform.radiusSurface, GroupRadius))
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SmallGap)) {
        SectionLabel(label)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(c.bgRaised.copy(alpha = if (c.dark) GROUP_DARK else GROUP_LIGHT))
                .border(0.5.dp, c.glassEdge, shape),
            content = content,
        )
    }
}

/** One setting: its name, what it does, and its control at the end. */
@Composable
fun SettingRow(
    title: String,
    description: String? = null,
    /** A path or command, in the mono face. */
    mono: String? = null,
    /** Why the value cannot be used, in Octo's own words. */
    error: String? = null,
    control: @Composable RowScope.() -> Unit = {},
) {
    val c = Pyrlyn.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = LargeGap, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LargeGap),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            description?.let { Text(it, style = Pyrlyn.type.small, color = c.fgMuted) }
            mono?.let {
                Text(
                    it,
                    style = Pyrlyn.type.mono.copy(fontSize = Pyrlyn.type.small.fontSize),
                    color = c.fgMuted,
                )
            }
            error?.let { Text("⊗ $it", style = Pyrlyn.type.small, color = c.danger) }
        }
        control()
    }
}

private val SwitchWidth = 34.dp
private val SwitchHeight = 20.dp
private val ThumbSize = 16.dp
private val GroupRadius = 12.dp
private const val GROUP_DARK = 0.6f
private const val GROUP_LIGHT = 0.85f
