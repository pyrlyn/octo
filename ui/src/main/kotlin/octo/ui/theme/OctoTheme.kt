package octo.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import octo.core.config.RowDensity
import octo.core.paths.HostOs

/** Pyrlyn's desktop recipes: each OS keeps its own radii, control height and body size. */
@Immutable
internal class GlassPlatform private constructor(
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

        fun of(
            os: HostOs,
            density: RowDensity = RowDensity.Normal,
        ): GlassPlatform =
            when (os) {
                HostOs.MacOs -> MacOS
                HostOs.Windows -> Windows
                HostOs.Linux -> Linux
            }.let { base ->
                val step = density.stepDp.dp
                with(base) {
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
            }
    }
}

@Immutable
internal data class PyrlynType(
    val body: TextStyle,
    val small: TextStyle,
    val label: TextStyle,
    val title: TextStyle,
    val mono: TextStyle,
)

/** IBM Plex Sans and JetBrains Mono (SIL OFL 1.1) ship in the jar, so every OS reads the same. */
private object PyrlynFonts {
    val ui =
        FontFamily(
            Font("fonts/IBMPlexSans-Regular.ttf", FontWeight.Normal),
            Font("fonts/IBMPlexSans-Medium.ttf", FontWeight.Medium),
            Font("fonts/IBMPlexSans-SemiBold.ttf", FontWeight.SemiBold),
            Font("fonts/IBMPlexSans-Bold.ttf", FontWeight.Bold),
        )
    val mono =
        FontFamily(
            Font("fonts/JetBrainsMono-Regular.ttf", FontWeight.Normal),
            Font("fonts/JetBrainsMono-Medium.ttf", FontWeight.Medium),
            Font("fonts/JetBrainsMono-Bold.ttf", FontWeight.Bold),
        )
}

private fun typeFor(platform: GlassPlatform): PyrlynType {
    val body = platform.bodySize
    val ui = PyrlynFonts.ui
    return PyrlynType(
        body = TextStyle(fontFamily = ui, fontSize = body.sp, lineHeight = (body + 5).sp),
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
        mono = TextStyle(fontFamily = PyrlynFonts.mono, fontSize = (body - 1).sp, lineHeight = (body + 5).sp),
    )
}

private val LocalPyrlynColors = staticCompositionLocalOf { PyrlynDark }
private val LocalGlassPlatform = staticCompositionLocalOf { GlassPlatform.MacOS }
private val LocalPyrlynType = staticCompositionLocalOf { typeFor(GlassPlatform.MacOS) }
private val LocalOpaque = staticCompositionLocalOf { false }

internal object Pyrlyn {
    val colors: PyrlynColors
        @Composable get() = LocalPyrlynColors.current
    val platform: GlassPlatform
        @Composable get() = LocalGlassPlatform.current
    val type: PyrlynType
        @Composable get() = LocalPyrlynType.current

    /** Solid surfaces instead of glass, from Settings > Appearance. */
    val opaque: Boolean
        @Composable get() = LocalOpaque.current
    val controlShape: RoundedCornerShape
        @Composable get() = RoundedCornerShape(platform.radiusControl)
    val surfaceShape: RoundedCornerShape
        @Composable get() = RoundedCornerShape(platform.radiusSurface)
}

/**
 * Pyrlyn Glass for the whole UI. Material 3 components stay, so the tokens are also mapped into
 * its colour scheme, type and shapes; glass surfaces read [Pyrlyn] directly.
 */
@Composable
internal fun OctoTheme(
    dark: Boolean,
    platform: GlassPlatform,
    opaque: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = if (dark) PyrlynDark else PyrlynLight
    val type = remember(platform) { typeFor(platform) }
    CompositionLocalProvider(
        LocalPyrlynColors provides colors,
        LocalGlassPlatform provides platform,
        LocalPyrlynType provides type,
        LocalOpaque provides opaque,
    ) {
        MaterialTheme(colorScheme(colors), shapes(platform), typography(type)) {
            // No root Surface sets it: the window is glass over the backdrop.
            // A desktop pointer needs no 48dp touch target; Pyrlyn sizes controls per platform.
            CompositionLocalProvider(
                LocalContentColor provides colors.fg,
                LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
                content = content,
            )
        }
    }
}

private fun colorScheme(c: PyrlynColors): ColorScheme =
    ColorScheme(
        primary = c.accentSolid,
        onPrimary = c.onAccent,
        primaryContainer = c.accentBg,
        onPrimaryContainer = c.stateSelectedFg,
        inversePrimary = c.accentFg,
        secondary = c.accentFg,
        onSecondary = c.onAccent,
        secondaryContainer = c.accentBg,
        onSecondaryContainer = c.stateSelectedFg,
        tertiary = c.info,
        onTertiary = c.onAccent,
        tertiaryContainer = c.info.copy(alpha = TINT),
        onTertiaryContainer = c.fg,
        background = c.bgApp,
        onBackground = c.fg,
        // Compose has no backdrop blur, so floating surfaces stay opaque: text behind must not show.
        surface = c.veilFloating.compositeOver(c.solidFloating),
        onSurface = c.fg,
        surfaceVariant = c.bgSunken,
        onSurfaceVariant = c.fgMuted,
        surfaceTint = Color.Transparent,
        inverseSurface = c.fg,
        inverseOnSurface = c.bgCanvas,
        error = c.danger,
        onError = Color.White,
        errorContainer = c.danger.copy(alpha = TINT),
        onErrorContainer = c.fg,
        outline = c.borderStrong,
        outlineVariant = c.border,
        scrim = c.scrim,
        surfaceBright = c.bgRaised,
        surfaceContainer = c.veilFloating.compositeOver(c.solidFloating),
        surfaceContainerHigh = c.veilFloating.compositeOver(c.solidFloating),
        surfaceContainerHighest = c.bgActive,
        surfaceContainerLow = c.bgSunken,
        surfaceContainerLowest = c.bgCanvas,
        surfaceDim = c.bgApp,
        primaryFixed = c.accentBg,
        primaryFixedDim = c.accentBg,
        onPrimaryFixed = c.stateSelectedFg,
        onPrimaryFixedVariant = c.stateSelectedFg,
        secondaryFixed = c.accentBg,
        secondaryFixedDim = c.accentBg,
        onSecondaryFixed = c.stateSelectedFg,
        onSecondaryFixedVariant = c.stateSelectedFg,
        tertiaryFixed = c.info.copy(alpha = TINT),
        tertiaryFixedDim = c.info.copy(alpha = TINT),
        onTertiaryFixed = c.fg,
        onTertiaryFixedVariant = c.fg,
    )

private fun shapes(p: GlassPlatform) =
    Shapes(
        extraSmall = RoundedCornerShape(p.radiusControl),
        small = RoundedCornerShape(p.radiusControl),
        medium = RoundedCornerShape(p.radiusSurface),
        large = RoundedCornerShape(p.radiusSurface),
        extraLarge = RoundedCornerShape(p.radiusWindow),
    )

private fun typography(t: PyrlynType): Typography {
    val medium = t.body.copy(fontWeight = FontWeight.Medium)
    val semibold = t.body.copy(fontWeight = FontWeight.SemiBold)
    return Typography(
        displayLarge = t.title,
        displayMedium = t.title,
        displaySmall = t.title,
        headlineLarge = t.title,
        headlineMedium = t.title,
        headlineSmall = t.title,
        titleLarge = t.title,
        titleMedium = semibold,
        titleSmall = semibold,
        bodyLarge = t.body,
        bodyMedium = t.body,
        bodySmall = t.small,
        labelLarge = medium,
        labelMedium = t.label,
        labelSmall = t.label,
    )
}

/** Alpha of a status colour used as a container tint. */
private const val TINT = 0.16f
