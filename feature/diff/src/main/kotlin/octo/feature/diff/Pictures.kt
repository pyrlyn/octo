package octo.feature.diff

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface
import org.jetbrains.skia.svg.SVGDOM
import org.jetbrains.skia.svg.SVGLengthUnit
import org.jetbrains.skia.svg.SVGSVG
import kotlin.math.max

/** Decodes pictures with Skia, which Compose already ships. */
internal object Pictures {
    // A small file can declare a huge canvas; decoding it would take gigabytes.
    private const val MAX_PIXELS = 40_000_000L
    private const val MAX_SIDE = 4096f
    private const val DEFAULT_WIDTH = 800f
    private const val DEFAULT_HEIGHT = 600f

    fun raster(bytes: ByteArray): ImageBitmap {
        val codec = Codec.makeFromData(Data.makeFromBytes(bytes))
        require(codec.width.toLong() * codec.height <= MAX_PIXELS) {
            "The image is too large to preview (${codec.width}×${codec.height})"
        }
        return Image.makeFromEncoded(bytes).toComposeImageBitmap()
    }

    fun svg(bytes: ByteArray): ImageBitmap {
        val dom = SVGDOM(Data.makeFromBytes(bytes))
        val root = requireNotNull(dom.root) { "Not an SVG image" }
        val (width, height) = size(root)
        val scale = minOf(1f, MAX_SIDE / max(width, height))
        val w = (width * scale).toInt().coerceAtLeast(1)
        val h = (height * scale).toInt().coerceAtLeast(1)
        dom.setContainerSize(w.toFloat(), h.toFloat())
        val surface = Surface.makeRasterN32Premul(w, h)
        try {
            dom.render(surface.canvas)
            return surface.makeImageSnapshot().toComposeImageBitmap()
        } finally {
            surface.close()
        }
    }

    // Width and height may be percentages, which mean nothing without a page; the view box does not.
    private fun size(root: SVGSVG): Pair<Float, Float> {
        root.viewBox?.takeIf { it.width > 0 && it.height > 0 }?.let { return it.width to it.height }
        val absolute = setOf(SVGLengthUnit.NUMBER, SVGLengthUnit.PX)
        val width = root.width.takeIf { it.unit in absolute && it.value > 0 }?.value ?: DEFAULT_WIDTH
        val height = root.height.takeIf { it.unit in absolute && it.value > 0 }?.value ?: DEFAULT_HEIGHT
        return width to height
    }
}
