package octo.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp

/** An SVG icon, or a monogram of [name] when there is none or it does not parse. */
@Composable
fun Glyph(
    svg: String?,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val painter = remember(svg) { svg?.let(::svgPainter) }
    if (painter != null) {
        Image(painter, contentDescription = name, modifier = modifier.size(size))
        return
    }
    AccentTile(name.firstOrNull()?.uppercase() ?: "?", size, Pyrlyn.type.label, modifier)
}

/** Null for markup that cannot be read, so the caller falls back to the monogram. */
internal expect fun svgPainter(svg: String): Painter?
