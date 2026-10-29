package octo.ui.window

import kotlinx.coroutines.test.runTest
import octo.core.paths.HostOs
import octo.core.paths.currentHostOs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeWindowTest {
    @Test
    fun `gsettings false means animations are off`() {
        assertTrue(animationsOff("false\n"))
        assertFalse(animationsOff("true\n"))
        // An error message or an unknown key is not a request for less motion.
        assertFalse(animationsOff("No such key “enable-animations”\n"))
    }

    @Test
    fun `each OS starts with the frame the design draws for it`() {
        assertEquals(Chrome.MacOs, NativeWindow.of(HostOs.MacOs).chrome)
        assertEquals(Chrome.Caption, NativeWindow.of(HostOs.Windows).chrome)
        assertEquals(Chrome.HeaderBar, NativeWindow.of(HostOs.Linux).chrome)
    }

    @Test
    fun `macOS accessibility settings read through AppKit`() =
        runTest {
            // The Foreign Function calls only exist on the OS they were written for.
            if (currentHostOs() != HostOs.MacOs) return@runTest
            NativeWindow.of(HostOs.MacOs).settings()
        }
}
