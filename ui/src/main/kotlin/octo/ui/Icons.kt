package octo.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import octo.core.agent.AgentIcon
import org.jetbrains.skia.Data
import org.jetbrains.skia.svg.SVGDOM

private const val ICON_VIEWPORT = 24f
private const val STOP_FROM = 6f
private const val STOP_TO = 18f
private const val CORNER_SHARE = 25

/** A filled square, drawn here because the UI ships no icon library. */
internal val StopIcon: ImageVector =
    ImageVector
        .Builder("Stop", ICON_VIEWPORT.dp, ICON_VIEWPORT.dp, ICON_VIEWPORT, ICON_VIEWPORT)
        .addPath(
            PathData {
                moveTo(STOP_FROM, STOP_FROM)
                lineTo(STOP_TO, STOP_FROM)
                lineTo(STOP_TO, STOP_TO)
                lineTo(STOP_FROM, STOP_TO)
                close()
            },
            fill = SolidColor(Color.Black),
        ).build()

/** The adapter's SVG, or a monogram of [name] when there is none or it does not parse. */
@Composable
internal fun AgentGlyph(
    icon: AgentIcon?,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val painter = remember(icon) { icon?.let(::svgPainter) }
    if (painter != null) {
        Image(painter, contentDescription = name, modifier = modifier.size(size))
        return
    }
    Box(
        modifier
            .size(size)
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(CORNER_SHARE)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

// Skia renders the SVG directly; markup it cannot read falls back to the monogram.
@Suppress("TooGenericExceptionCaught")
private fun svgPainter(icon: AgentIcon): Painter? =
    try {
        SVGDOM(Data.makeFromBytes(icon.svg.encodeToByteArray())).takeIf { it.root != null }?.let(::SvgPainter)
    } catch (_: RuntimeException) {
        null
    }

private class SvgPainter(
    private val dom: SVGDOM,
) : Painter() {
    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() {
        dom.setContainerSize(size.width, size.height)
        drawIntoCanvas { dom.render(it.skiaCanvas) }
    }
}
