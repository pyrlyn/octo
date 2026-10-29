package octo.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Windows' caption buttons: minimize, maximize or restore, and close, which turns red under the pointer. */
@Composable
fun CaptionButtons(
    maximized: Boolean,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxHeight()) {
        CaptionButton(onMinimize, Pyrlyn.colors.stateHover) { color ->
            drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), GLYPH_STROKE)
        }
        CaptionButton(onToggleMaximize, Pyrlyn.colors.stateHover) { color ->
            if (maximized) {
                val step = size.width * RESTORE_STEP
                drawRect(
                    color,
                    Offset(step, 0f),
                    Size(size.width - step, size.height - step),
                    style = Stroke(GLYPH_STROKE),
                )
                drawRect(
                    color,
                    Offset(0f, step),
                    Size(size.width - step, size.height - step),
                    style = Stroke(GLYPH_STROKE),
                )
            } else {
                drawRect(color, style = Stroke(GLYPH_STROKE))
            }
        }
        CaptionButton(onClose, CloseRed, Color.White) { color -> cross(color) }
    }
}

/** A round close button, as in a libadwaita header bar. */
@Composable
fun CloseCircle(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Pyrlyn.colors
    Box(
        modifier
            .size(CircleSize)
            .clip(CircleShape)
            .background(c.stateHover)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(GlyphSize * CIRCLE_GLYPH)) { cross(c.fg) }
    }
}

@Composable
private fun CaptionButton(
    onClick: () -> Unit,
    hover: Color,
    hoverGlyph: Color? = null,
    glyph: DrawScope.(Color) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val color = if (hovered && hoverGlyph != null) hoverGlyph else Pyrlyn.colors.fg
    Box(
        Modifier
            .width(CaptionButtonWidth)
            .fillMaxHeight()
            .background(if (hovered) hover else Color.Transparent)
            .hoverable(interaction)
            .clickable(interaction, null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(GlyphSize)) { glyph(color) }
    }
}

private fun DrawScope.cross(color: Color) {
    drawLine(color, Offset.Zero, Offset(size.width, size.height), GLYPH_STROKE)
    drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), GLYPH_STROKE)
}

// Windows 11's sizes and its close-button red.
private val CaptionButtonWidth = 46.dp
private val GlyphSize = 10.dp
private val CircleSize = 24.dp
private const val CIRCLE_GLYPH = 0.8f
private const val GLYPH_STROKE = 1f
private const val RESTORE_STEP = 0.2f
private val CloseRed = Color(0xFFC42B1C)
