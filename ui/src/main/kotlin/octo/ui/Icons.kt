package octo.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.unit.Dp
import octo.core.agent.AgentIcon
import octo.ui.theme.Pyrlyn
import org.jetbrains.skia.Data
import org.jetbrains.skia.svg.SVGDOM

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
    AccentTile(name.firstOrNull()?.uppercase() ?: "?", size, Pyrlyn.type.label, modifier)
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
