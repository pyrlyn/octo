package octo.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * Renders a sample of borders, dividers and text at the common screen scales into the directory
 * named by OCTO_SHOTS, so sharpness can be compared across platforms. Skipped when it is unset.
 */
class CrispShotsTest {
    @Test
    fun rendersTheSampleAtEveryScale() {
        val out = System.getenv("OCTO_SHOTS")?.let(::File)?.apply { mkdirs() } ?: return
        val os = System.getProperty("os.name").substringBefore(' ').lowercase()
        for (scale in SCALES) {
            val scene =
                ImageComposeScene((WIDTH * scale).toInt(), (HEIGHT * scale).toInt(), Density(scale)) { Sample() }
            val png = scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes
            File(out, "$os-${(scale * PERCENT).toInt()}.png").writeBytes(png)
            scene.close()
        }
    }

    @androidx.compose.runtime.Composable
    private fun Sample() {
        PyrlynTheme(dark = true, platform = GlassPlatform.Windows) {
            Column(Modifier.background(Color(BACKGROUND)).padding(12.dp)) {
                Box(Modifier.border(1.dp.snapped, Pyrlyn.colors.glassRim, RoundedCornerShape(6.dp)).padding(8.dp)) {
                    Text("Settings  ·  Permissions  0123 Ag", style = Pyrlyn.type.body)
                }
                Box(
                    Modifier
                        .padding(vertical = 6.dp)
                        .fillMaxWidth()
                        .height(hairline)
                        .background(Pyrlyn.colors.glassEdge),
                )
                Text("Small label · Run tests · fix blur", style = Pyrlyn.type.small)
                Text("mono: val x = 42 // ok", style = Pyrlyn.type.mono)
            }
        }
    }

    private companion object {
        val SCALES = listOf(1f, 1.25f, 1.5f, 1.75f, 2f)
        const val WIDTH = 360
        const val HEIGHT = 150
        const val PERCENT = 100
        const val BACKGROUND = 0xFF1E1E24
    }
}
