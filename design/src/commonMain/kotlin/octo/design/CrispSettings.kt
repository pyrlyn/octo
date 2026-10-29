package octo.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * How text and hairlines meet the pixel grid, per platform and screen scale. Kept apart from any
 * renderer, so the current Skia (software) path and a future native one read the same settings.
 */
interface CrispSettings {
    /** Glyph smoothing, hinting and positioning for text drawn at [scale] device pixels per dp. */
    fun text(scale: Float): PlatformTextStyle?
}

/** The settings for the OS the app runs on. */
internal expect val HostCrisp: CrispSettings

/** [width] rounded to whole device pixels and never thinner than one, so a border stays sharp. */
fun snapToPixels(
    width: Dp,
    scale: Float,
): Dp = (max(1, (width.value * scale).roundToInt()) / scale).dp

/** A 0.5dp rule snapped to whole device pixels: one pixel at 1x, 1.5x and 2x, never a smeared fraction. */
val hairline: Dp
    @Composable get() = snapToPixels(HAIRLINE, LocalDensity.current.density)

private val HAIRLINE = 0.5.dp
