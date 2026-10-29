package octo.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

/** How much of what is behind a surface shows through it. */
enum class GlassLevel { Chrome, Floating, Control, Content }

/** Pyrlyn's elevation steps: shadow radius of the ambient and contact layers. */
class Elevation private constructor(
    val ambient: Float,
    val contact: Float,
) {
    companion object {
        val E1 = Elevation(4f, 1f)
        val E2 = Elevation(28f, 2f)
        val E3 = Elevation(56f, 4f)
    }
}

/**
 * A Pyrlyn Glass surface: layered shadow, veil and tint, sheen, specular top line, caustic and rim.
 * Compose has no backdrop blur, so the glass reads against [GlassBackdrop] instead of the desktop.
 */
@Composable
fun Modifier.glass(
    shape: Shape,
    level: GlassLevel,
    elevation: Elevation = Elevation.E2,
): Modifier {
    val c = Pyrlyn.colors
    val p = Pyrlyn.platform
    val solid = Pyrlyn.opaque
    val veil =
        when (level) {
            GlassLevel.Chrome -> if (solid) c.solidChrome else c.veilChrome
            GlassLevel.Floating -> if (solid) c.solidFloating else c.veilFloating
            GlassLevel.Control -> if (solid) c.solidFloating else c.veilControl
            GlassLevel.Content -> if (solid) c.bgCanvas else c.veilContent
        }
    val highlight = c.specular.copy(alpha = c.specular.alpha * p.highlight)
    return this
        .shadow(
            (elevation.ambient * p.depth / AMBIENT_SHARE).dp,
            shape,
            ambientColor = c.shadowAmbient,
            spotColor = c.shadowKey,
        ).shadow((elevation.contact * p.depth).dp, shape, ambientColor = c.shadowContact, spotColor = c.shadowContact)
        .clip(shape)
        // Compose has no backdrop blur, so menus and dialogs must not let the text behind them show.
        .then(if (level == GlassLevel.Floating) Modifier.background(c.solidFloating) else Modifier)
        .background(veil)
        .background(if (solid) Color.Transparent else c.glassTint)
        // Drawn behind the content, so text keeps its full contrast.
        .drawBehind {
            if (!solid) drawSheen(c)
            val inset = size.height.coerceAtMost(HIGHLIGHT_INSET)
            drawLine(highlight, Offset(inset, HAIRLINE), Offset(size.width - inset, HAIRLINE), 1.dp.toPx())
        }.border(1.dp, Brush.verticalGradient(listOf(c.glassRim, c.glassEdge)), shape)
}

/** The window's own chrome, flush with its edges: a header bar, a flush sidebar. */
@Composable
fun Modifier.chromeVeil(): Modifier =
    background(if (Pyrlyn.opaque) Pyrlyn.colors.solidChrome else Pyrlyn.colors.veilChrome)

private fun DrawScope.drawSheen(c: PyrlynColors) {
    drawRect(
        Brush.verticalGradient(
            0f to c.specularSoft,
            SHEEN_END to Color.Transparent,
            CAUSTIC_START to Color.Transparent,
            1f to c.caustic,
        ),
    )
}

/** The blurred wallpaper the glass reads against, a stand-in for the desktop behind the window. */
@Composable
fun GlassBackdrop(modifier: Modifier = Modifier) {
    val c = Pyrlyn.colors
    val strong = if (c.dark) DARK_GLOW else LIGHT_GLOW
    Box(
        modifier
            .background(c.wallpaper4)
            .drawBehind {
                listOf(c.wallpaper1, c.wallpaper2, c.wallpaper3).zip(Glows) { color, glow ->
                    drawRect(
                        Brush.radialGradient(
                            listOf(color.copy(alpha = strong * glow.strength), Color.Transparent),
                            center = Offset(size.width * glow.x, size.height * glow.y),
                            radius = size.maxDimension * glow.radius,
                        ),
                    )
                }
            },
    )
}

/** Where each wallpaper colour glows, as shares of the window. */
private class Glow(
    val x: Float,
    val y: Float,
    val radius: Float,
    val strength: Float,
)

private val Glows = listOf(Glow(0.15f, 0.2f, 0.6f, 1f), Glow(0.95f, 1.1f, 0.7f, 1f), Glow(0.7f, 0f, 0.45f, 0.5f))

private const val AMBIENT_SHARE = 4f
private const val SHEEN_END = 0.45f
private const val CAUSTIC_START = 0.85f
private const val HIGHLIGHT_INSET = 24f
private const val HAIRLINE = 0.5f
private const val DARK_GLOW = 0.8f
private const val LIGHT_GLOW = 0.3f
