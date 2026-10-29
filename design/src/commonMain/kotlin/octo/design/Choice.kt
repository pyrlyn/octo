package octo.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** A checkbox; with a null [onCheckedChange] it only shows the value, for a row that takes the click. */
@Composable
fun GlassCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val toggle =
        onCheckedChange?.let {
            Modifier.toggleable(checked, interaction, null, enabled, Role.Checkbox, it)
        } ?: Modifier
    val mark = Pyrlyn.colors.onAccent
    Mark(checked, enabled, BoxShape, interaction, modifier.then(toggle)) {
        if (checked) {
            Box(
                Modifier.size(MarkSize).drawBehind {
                    val tick =
                        Path().apply {
                            moveTo(size.width * TICK_START_X, size.height * TICK_START_Y)
                            lineTo(size.width * TICK_MID_X, size.height * TICK_MID_Y)
                            lineTo(size.width * TICK_END_X, size.height * TICK_END_Y)
                        }
                    drawPath(
                        tick,
                        mark,
                        style = Stroke(TickWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                },
            )
        }
    }
}

/** A radio button; with a null [onClick] it only shows the value, for a row that takes the click. */
@Composable
fun GlassRadio(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val select =
        onClick?.let { Modifier.selectable(selected, interaction, null, enabled, Role.RadioButton, it) } ?: Modifier
    val dot = Pyrlyn.colors.onAccent
    Mark(selected, enabled, CircleShape, interaction, modifier.then(select)) {
        if (selected) {
            Box(
                Modifier.size(RadioDot).drawBehind {
                    drawCircle(
                        dot,
                        radius = size.minDimension / 2,
                        center =
                            Offset(size.width / 2, size.height / 2),
                    )
                },
            )
        }
    }
}

@Composable
private fun Mark(
    on: Boolean,
    enabled: Boolean,
    shape: Shape,
    interaction: MutableInteractionSource,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val c = Pyrlyn.colors
    val hovered by interaction.collectIsHoveredAsState()
    val fill =
        when {
            on && enabled -> c.accentSolid
            on -> c.fgDisabled
            else -> c.bgSunken
        }
    val edge =
        when {
            on -> fill
            !enabled -> c.border
            hovered -> c.fgMuted
            else -> c.borderStrong
        }
    Box(
        modifier
            .hoverable(interaction, enabled)
            .size(MarkSize)
            .clip(shape)
            .background(fill)
            .border(1.dp, edge, shape),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * One option with its label and an optional note under it, as a radio button or, with [multiple],
 * a checkbox. The whole row takes the click.
 */
@Composable
fun Choice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
    enabled: Boolean = true,
    multiple: Boolean = false,
    /** Inside the clickable area, for a row that spans a settings group. */
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val c = Pyrlyn.colors
    val interaction = remember { MutableInteractionSource() }
    val pick =
        if (multiple) {
            Modifier.toggleable(selected, interaction, null, enabled, Role.Checkbox) { onClick() }
        } else {
            Modifier.selectable(selected, interaction, null, enabled, Role.RadioButton, onClick)
        }
    Row(
        modifier.then(pick).padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Gap),
    ) {
        if (multiple) {
            GlassCheckbox(
                selected,
                null,
                enabled = enabled,
            )
        } else {
            GlassRadio(selected, null, enabled = enabled)
        }
        Column {
            Text(label, color = if (enabled) c.fg else c.fgDisabled)
            note?.let { Text(it, style = Pyrlyn.type.small, color = c.fgMuted) }
        }
    }
}

/** A small removable tag, such as a file waiting to be sent; a click on it calls [onClick]. */
@Composable
fun GlassChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    val c = Pyrlyn.colors
    val shape = Pyrlyn.controlShape
    Row(
        modifier
            .clip(shape)
            .background(c.stateHover)
            .border(hairline, c.glassRim, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Gap, vertical = SmallGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text, style = Pyrlyn.type.small, maxLines = 1)
        trailing?.let { Text(it, style = Pyrlyn.type.small, color = c.fgMuted) }
    }
}

private val MarkSize = 16.dp
private val RadioDot = 6.dp
private val TickWidth = 1.6.dp
private val BoxShape = RoundedCornerShape(4.dp)
private const val TICK_START_X = 0.24f
private const val TICK_START_Y = 0.52f
private const val TICK_MID_X = 0.42f
private const val TICK_MID_Y = 0.70f
private const val TICK_END_X = 0.76f
private const val TICK_END_Y = 0.32f
