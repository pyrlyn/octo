// The icons of the apps a worktree opens in, read the way each OS keeps them.
package octo.feature.common

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import javax.imageio.ImageIO
import javax.swing.filechooser.FileSystemView

/**
 * The icon of the app at [path], at least [size] pixels where the app has one that big; null when none can be read.
 * It reads files, so call it off the UI thread.
 */
fun appIcon(
    path: String,
    size: Int,
): ImageBitmap? =
    runCatching {
        // Swing gives a macOS bundle only the generic folder icon, so its own .icns is read instead.
        val image = if (path.endsWith(".app")) bundleIcon(File(path), size) else shellIcon(File(path), size)
        image?.toComposeImageBitmap()
    }.getOrNull()

private fun bundleIcon(
    bundle: File,
    size: Int,
): BufferedImage? {
    val resources = File(bundle, "Contents/Resources")
    val named = iconFileName(File(bundle, "Contents/Info.plist"))
    // A binary Info.plist cannot be read without a parser; such bundles name their icon after themselves.
    val candidates =
        listOfNotNull(named, bundle.name.removeSuffix(".app"))
            .map { File(resources, if (it.endsWith(".icns")) it else "$it.icns") } +
            resources.listFiles { file -> file.extension == "icns" }.orEmpty().sortedBy { it.name }
    val png = candidates.firstOrNull { it.isFile }?.let { icnsPng(it.readBytes(), size) } ?: return null
    return ImageIO.read(ByteArrayInputStream(png))
}

private fun iconFileName(plist: File): String? =
    plist
        .takeIf { it.isFile }
        ?.readText()
        ?.let { ICON_FILE_KEY.find(it) }
        ?.groupValues
        ?.get(1)
        ?.trim()
        ?.takeIf { it.isNotEmpty() && '/' !in it }

/** Windows draws an .exe's own icon; elsewhere this gives a generic one. */
private fun shellIcon(
    file: File,
    size: Int,
): BufferedImage? {
    val icon = FileSystemView.getFileSystemView().getSystemIcon(file, size, size) ?: return null
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    try {
        icon.paintIcon(null, graphics, (size - icon.iconWidth) / 2, (size - icon.iconHeight) / 2)
    } finally {
        graphics.dispose()
    }
    return image
}

/**
 * The PNG in an .icns file closest to [size] pixels from above, or the largest when none is that big. An .icns is a
 * list of typed chunks; the modern ones hold PNGs, the older ones raw bitmaps this skips.
 */
internal fun icnsPng(
    bytes: ByteArray,
    size: Int,
): ByteArray? {
    if (bytes.size < CHUNK_HEADER || String(bytes, 0, TYPE_LENGTH, Charsets.ISO_8859_1) != "icns") return null
    val buffer = ByteBuffer.wrap(bytes)
    val pngs = mutableListOf<Pair<Int, ByteArray>>()
    var at = CHUNK_HEADER
    while (at + CHUNK_HEADER <= bytes.size) {
        val type = String(bytes, at, TYPE_LENGTH, Charsets.ISO_8859_1)
        val length = buffer.getInt(at + TYPE_LENGTH)
        // The file comes from another app, so a chunk that runs past the end stops the walk.
        if (length < CHUNK_HEADER || length > bytes.size - at) break
        val pixels = ICNS_PNG_SIZES[type]
        val data = bytes.copyOfRange(at + CHUNK_HEADER, at + length)
        if (pixels != null && data.take(PNG_MAGIC.size) == PNG_MAGIC) pngs += pixels to data
        at += length
    }
    return (pngs.filter { it.first >= size }.minByOrNull { it.first } ?: pngs.maxByOrNull { it.first })?.second
}

private const val TYPE_LENGTH = 4
private const val CHUNK_HEADER = 8
private val PNG_MAGIC = listOf<Byte>(-119, 80, 78, 71)
private val ICON_FILE_KEY = Regex("<key>CFBundleIconFile</key>\\s*<string>([^<]+)</string>")

// Pixel sizes of the chunk types that hold PNGs, the @2x ones included.
private val ICNS_PNG_SIZES =
    mapOf(
        "icp4" to 16,
        "icp5" to 32,
        "icp6" to 64,
        "ic11" to 32,
        "ic12" to 64,
        "ic07" to 128,
        "ic13" to 256,
        "ic08" to 256,
        "ic14" to 512,
        "ic09" to 512,
        "ic10" to 1024,
    )
