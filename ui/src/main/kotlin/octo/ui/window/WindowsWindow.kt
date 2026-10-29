package octo.ui.window

import androidx.compose.ui.awt.ComposeWindow
import org.jetbrains.skiko.SkiaLayer
import java.awt.AlphaComposite
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Container
import java.awt.Graphics
import java.awt.Graphics2D
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_INT
import javax.swing.JPanel

/**
 * Windows: a caption bar drawn by the shell, rounded corners, and Mica behind the content on
 * Windows 11 22H2 and later. Older systems refuse the backdrop attribute and keep the in-app one.
 */
internal object WindowsWindow : NativeWindow {
    override val chrome = Chrome.Caption

    // A transparent Compose window is a layered one, which DWM draws no backdrop under; the window
    // stays opaque and only its Skia layer turns transparent once Mica is on.
    override val transparent = false

    private val dwm by lazy { SymbolLookup.libraryLookup("dwmapi", Arena.global()) }
    private val setAttribute by lazy {
        function(dwm, "DwmSetWindowAttribute", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT))
    }
    private val extendFrame by lazy {
        function(dwm, "DwmExtendFrameIntoClientArea", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS))
    }
    private val user32 by lazy { SymbolLookup.libraryLookup("user32", Arena.global()) }
    private val parametersInfo by lazy {
        function(
            user32,
            "SystemParametersInfoW",
            FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT),
        )
    }
    private val advapi32 by lazy { SymbolLookup.libraryLookup("advapi32", Arena.global()) }
    private val regGetValue by lazy {
        function(
            advapi32,
            "RegGetValueW",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, ADDRESS),
        )
    }

    override suspend fun decorate(window: ComposeWindow): Boolean {
        val hwnd = MemorySegment.ofAddress(window.windowHandle)
        // Windows 10 has no rounded corners and refuses this; the window is square there.
        setInt(hwnd, CORNER_PREFERENCE, ROUND)
        if (setInt(hwnd, SYSTEM_BACKDROP_TYPE, MAIN_WINDOW) != S_OK) return false
        // Negative margins make the whole window one sheet of glass, so Mica fills the client area.
        Arena.ofConfined().use { arena ->
            val margins = arena.allocate(MemoryLayout.sequenceLayout(MARGIN_SIDES, JAVA_INT))
            repeat(MARGIN_SIDES.toInt()) { margins.setAtIndex(JAVA_INT, it.toLong(), -1) }
            extendFrame.invokeWithArguments(hwnd, margins)
        }
        window.findSkiaLayer()?.transparency = true
        window.clearContentPane()
        return true
    }

    override fun setDark(
        window: ComposeWindow,
        dark: Boolean,
    ) {
        setInt(MemorySegment.ofAddress(window.windowHandle), USE_IMMERSIVE_DARK_MODE, if (dark) 1 else 0)
    }

    override suspend fun settings(): OsSettings =
        Arena.ofConfined().use { arena ->
            val animate = arena.allocate(JAVA_INT)
            val animations = parametersInfo.invokeWithArguments(GET_CLIENT_AREA_ANIMATION, 0, animate, 0) as Int != 0
            val data = arena.allocate(JAVA_INT)
            val size = arena.allocate(JAVA_INT).also { it.set(JAVA_INT, 0, Int.SIZE_BYTES) }
            val read =
                regGetValue.invokeWithArguments(
                    MemorySegment.ofAddress(HKEY_CURRENT_USER),
                    arena.allocateFrom(PERSONALIZE, Charsets.UTF_16LE),
                    arena.allocateFrom("EnableTransparency", Charsets.UTF_16LE),
                    RRF_RT_REG_DWORD,
                    MemorySegment.NULL,
                    data,
                    size,
                ) as Int
            OsSettings(
                reducedTransparency = read == S_OK && data.get(JAVA_INT, 0) == 0,
                reducedMotion = animations && animate.get(JAVA_INT, 0) == 0,
            )
        }

    private fun setInt(
        hwnd: MemorySegment,
        attribute: Int,
        value: Int,
    ): Int =
        Arena.ofConfined().use { arena ->
            val data = arena.allocate(JAVA_INT).also { it.set(JAVA_INT, 0, value) }
            setAttribute.invokeWithArguments(hwnd, attribute, data, Int.SIZE_BYTES) as Int
        }

    private fun Container.findSkiaLayer(): SkiaLayer? =
        components.firstNotNullOfOrNull { it as? SkiaLayer ?: (it as? Container)?.findSkiaLayer() }

    // Swing paints the content pane opaque by default, which would cover the transparent layer.
    private fun ComposeWindow.clearContentPane() {
        val pane = ClearPane()
        pane.layout = BorderLayout()
        pane.background = Color(0, 0, 0, 0)
        contentPane.components.forEach { pane.add(it, BorderLayout.CENTER) }
        contentPane = pane
    }

    private class ClearPane : JPanel() {
        override fun paint(g: Graphics) {
            (g.create() as Graphics2D).apply {
                composite = AlphaComposite.Src
                color = background
                fillRect(0, 0, width, height)
                dispose()
            }
            super.paint(g)
        }
    }

    // From dwmapi.h, winuser.h and winreg.h.
    private const val S_OK = 0

    // MARGINS: left, right, top, bottom.
    private const val MARGIN_SIDES = 4L
    private const val USE_IMMERSIVE_DARK_MODE = 20
    private const val CORNER_PREFERENCE = 33
    private const val ROUND = 2
    private const val SYSTEM_BACKDROP_TYPE = 38
    private const val MAIN_WINDOW = 2
    private const val GET_CLIENT_AREA_ANIMATION = 0x1042
    private const val HKEY_CURRENT_USER = 0x80000001L
    private const val RRF_RT_REG_DWORD = 0x10
    private const val PERSONALIZE = "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize"
}
