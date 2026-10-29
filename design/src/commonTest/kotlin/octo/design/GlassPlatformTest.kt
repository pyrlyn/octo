package octo.design

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class GlassPlatformTest {
    @Test
    fun aRowStepChangesOnlyControlAndRowHeight() {
        val base = GlassPlatform.Windows
        val compact = base.withRowStep((-4).dp)
        assertEquals(base.controlHeight - 4.dp, compact.controlHeight)
        assertEquals(base.rowHeight - 4.dp, compact.rowHeight)
        assertEquals(base.radiusControl, compact.radiusControl)
        assertEquals(base.radiusSurface, compact.radiusSurface)
        assertEquals(base.bodySize, compact.bodySize)
        assertEquals(base.depth, compact.depth)
    }
}
