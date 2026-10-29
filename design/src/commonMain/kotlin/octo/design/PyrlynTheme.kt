package octo.design

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Pyrlyn's desktop recipes: each OS keeps its own radii, control height and body size. */
@Immutable
class GlassPlatform private constructor(
    val radiusControl: Dp,
    val radiusSurface: Dp,
    val radiusWindow: Dp,
    val controlHeight: Dp,
    val rowHeight: Dp,
    /** How strongly the specular top line shows. */
    val highlight: Float,
    /** Scales the glass shadows. */
    val depth: Float,
    val bodySize: Int,
) {
    companion object {
        val MacOS = GlassPlatform(8.dp, 14.dp, 18.dp, 28.dp, 28.dp, 0.75f, 1.5f, 13)
        val Windows = GlassPlatform(4.dp, 8.dp, 8.dp, 32.dp, 32.dp, 0.30f, 1.0f, 14)
        val Linux = GlassPlatform(6.dp, 12.dp, 12.dp, 34.dp, 34.dp, 0.30f, 1.1f, 14)
    }

    /** The same recipe with controls and rows [step] taller, for the row density setting. */
    fun withRowStep(step: Dp): GlassPlatform =
        GlassPlatform(
            radiusControl,
            radiusSurface,
            radiusWindow,
            controlHeight + step,
            rowHeight + step,
            highlight,
            depth,
            bodySize,
        )
}

@Immutable
data class PyrlynType(
    val body: TextStyle,
    /** Body text that heads a group, such as "Permissions" over its choices. */
    val strong: TextStyle,
    val small: TextStyle,
    val label: TextStyle,
    val title: TextStyle,
    val mono: TextStyle,
)

private fun typeFor(
    platform: GlassPlatform,
    raster: PlatformTextStyle? = null,
): PyrlynType {
    val body = platform.bodySize
    val ui = UiFont
    return PyrlynType(
        body = TextStyle(fontFamily = ui, fontSize = body.sp, lineHeight = (body + 5).sp),
        strong =
            TextStyle(
                fontFamily = ui,
                fontSize = body.sp,
                lineHeight = (body + 5).sp,
                fontWeight = FontWeight.SemiBold,
            ),
        small = TextStyle(fontFamily = ui, fontSize = (body - 2).sp, lineHeight = (body + 2).sp),
        label =
            TextStyle(
                fontFamily = ui,
                fontSize = (body - 2).sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.2.sp,
            ),
        title =
            TextStyle(
                fontFamily = ui,
                fontSize = (body + 4).sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = (body + 10).sp,
            ),
        mono = TextStyle(fontFamily = MonoFont, fontSize = (body - 1).sp, lineHeight = (body + 5).sp),
    ).let { t ->
        if (raster == null) {
            t
        } else {
            val r = TextStyle(platformStyle = raster)
            PyrlynType(t.body + r, t.strong + r, t.small + r, t.label + r, t.title + r, t.mono + r)
        }
    }
}

private val LocalPyrlynColors = staticCompositionLocalOf { PyrlynDark }
private val LocalGlassPlatform = staticCompositionLocalOf { GlassPlatform.MacOS }
private val LocalPyrlynType = staticCompositionLocalOf { typeFor(GlassPlatform.MacOS) }
private val LocalOpaque = staticCompositionLocalOf { false }
private val LocalReducedMotion = staticCompositionLocalOf { false }

object Pyrlyn {
    val colors: PyrlynColors
        @Composable get() = LocalPyrlynColors.current
    val platform: GlassPlatform
        @Composable get() = LocalGlassPlatform.current
    val type: PyrlynType
        @Composable get() = LocalPyrlynType.current

    /** Solid surfaces instead of glass, from Settings > Appearance. */
    val opaque: Boolean
        @Composable get() = LocalOpaque.current

    /** The OS asks for less motion: jump instead of animating. */
    val reducedMotion: Boolean
        @Composable get() = LocalReducedMotion.current
    val controlShape: RoundedCornerShape
        @Composable get() = RoundedCornerShape(platform.radiusControl)
    val surfaceShape: RoundedCornerShape
        @Composable get() = RoundedCornerShape(platform.radiusSurface)
}

/** Pyrlyn Glass for the whole UI: every component reads [Pyrlyn] and the content locals it sets. */
@Composable
fun PyrlynTheme(
    dark: Boolean,
    platform: GlassPlatform,
    opaque: Boolean = false,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = if (dark) PyrlynDark else PyrlynLight
    val scale = LocalDensity.current.density
    val type = remember(platform, scale) { typeFor(platform, HostCrisp.text(scale)) }
    val selection =
        remember(colors) {
            TextSelectionColors(colors.accentSolid, colors.accentSolid.copy(alpha = SELECTION_ALPHA))
        }
    val stateLayer =
        remember(colors) {
            StateLayer(colors.stateHover, colors.stateHover.copy(alpha = colors.stateHover.alpha * PRESS_DEPTH))
        }
    CompositionLocalProvider(
        LocalPyrlynColors provides colors,
        LocalGlassPlatform provides platform,
        LocalPyrlynType provides type,
        LocalOpaque provides opaque,
        LocalReducedMotion provides reducedMotion,
        // No root surface sets them: the window is glass over the backdrop.
        LocalContentColor provides colors.fg,
        LocalTextStyle provides type.body,
        LocalTextSelectionColors provides selection,
        LocalIndication provides stateLayer,
        content = content,
    )
}

private const val SELECTION_ALPHA = 0.35f
private const val PRESS_DEPTH = 2f
