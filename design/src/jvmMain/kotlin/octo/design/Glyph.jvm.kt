package octo.design

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.skiaCanvas
import org.jetbrains.skia.Data
import org.jetbrains.skia.svg.SVGDOM

// Skia renders the SVG directly.
@Suppress("TooGenericExceptionCaught")
internal actual fun svgPainter(svg: String): Painter? =
    try {
        SVGDOM(Data.makeFromBytes(svg.encodeToByteArray())).takeIf { it.root != null }?.let(::SvgPainter)
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
