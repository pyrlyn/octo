package octo.design

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class CrispTest {
    @Test
    fun aHairlineIsOneDevicePixelAtEveryCommonScale() {
        for (scale in listOf(1f, 1.25f, 1.5f, 1.75f, 2f)) {
            assertEquals(1f, snapToPixels(0.5.dp, scale).value * scale, 1e-4f, "scale $scale")
        }
    }

    @Test
    fun aWidthRoundsToWholeDevicePixels() {
        assertEquals(2f, snapToPixels(1.dp, 1.75f).value * 1.75f, 1e-4f)
        assertEquals(3f, snapToPixels(1.5.dp, 2f).value * 2f, 1e-4f)
    }
}
