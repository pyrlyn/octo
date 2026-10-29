@file:OptIn(ExperimentalTextApi::class)

package octo.design

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.FontHinting
import androidx.compose.ui.text.FontRasterizationSettings
import androidx.compose.ui.text.FontSmoothing
import androidx.compose.ui.text.PlatformParagraphStyle
import androidx.compose.ui.text.PlatformTextStyle

internal actual val HostCrisp: CrispSettings =
    System.getProperty("os.name").lowercase().let { os ->
        when {
            os.startsWith("mac") -> MacCrisp
            os.startsWith("windows") -> GridCrisp(FontHinting.Normal)
            else -> GridCrisp(FontHinting.Slight)
        }
    }

/**
 * macOS ignores hinting and has no LCD smoothing since Catalina; positioning glyphs on whole pixels
 * is what keeps text firm on a 1x external screen, while Retina keeps fractional positions.
 */
private object MacCrisp : CrispSettings {
    override fun text(scale: Float) =
        raster(
            FontRasterizationSettings(
                smoothing = FontSmoothing.AntiAlias,
                hinting = FontHinting.Normal,
                subpixelPositioning = scale >= HIGH_DENSITY,
                autoHintingForced = false,
            ),
        )
}

/**
 * Windows and Linux at 100-175%: snap glyph stems and positions to the pixel grid, as ClearType and
 * FreeType hinting do for native apps. Grayscale, not LCD: the window is transparent glass and LCD
 * smoothing on a transparent layer fringes. From 200% the grid is fine enough for subpixel layout.
 */
private class GridCrisp(
    private val hinting: FontHinting,
) : CrispSettings {
    override fun text(scale: Float): PlatformTextStyle {
        val dense = scale >= HIGH_DENSITY
        return raster(
            FontRasterizationSettings(
                smoothing = FontSmoothing.AntiAlias,
                hinting = if (dense) FontHinting.Slight else hinting,
                subpixelPositioning = dense,
                autoHintingForced = false,
            ),
        )
    }
}

private fun raster(settings: FontRasterizationSettings) =
    PlatformTextStyle(spanStyle = null, paragraphStyle = PlatformParagraphStyle(settings))

private const val HIGH_DENSITY = 2f
