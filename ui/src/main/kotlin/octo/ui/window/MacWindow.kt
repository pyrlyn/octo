package octo.ui.window

import androidx.compose.ui.awt.ComposeWindow
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * macOS: Compose only makes a window transparent when it is undecorated, so the window starts
 * borderless and gets its title bar back here, transparent and under the content, with the
 * system's own traffic lights. A visual-effect view behind the content is the material.
 */
internal object MacWindow : NativeWindow {
    override val chrome = Chrome.MacOs
    override val transparent = true

    override suspend fun decorate(window: ComposeWindow): Boolean {
        val handle = MemorySegment.ofAddress(window.windowHandle)
        return suspendCoroutine { done ->
            AppKitThread.run {
                runCatching { restyle(handle) }.fold({ done.resume(true) }, { done.resumeWithException(it) })
            }
        }
    }

    override fun setDark(
        window: ComposeWindow,
        dark: Boolean,
    ) {
        val handle = MemorySegment.ofAddress(window.windowHandle)
        AppKitThread.run {
            check(isAppWindow(handle)) { "not an app window" }
            val name = if (dark) "NSAppearanceNameDarkAqua" else "NSAppearanceNameAqua"
            val appearance = ObjC.send(ObjC.cls("NSAppearance"), "appearanceNamed:", nsString(name))
            ObjC.send(handle, "setAppearance:", appearance)
        }
    }

    override suspend fun settings(): OsSettings {
        val workspace = ObjC.send(ObjC.cls("NSWorkspace"), "sharedWorkspace")
        return OsSettings(
            reducedTransparency = ObjC.sendBool(workspace, "accessibilityDisplayShouldReduceTransparency"),
            reducedMotion = ObjC.sendBool(workspace, "accessibilityDisplayShouldReduceMotion"),
        )
    }

    private fun restyle(window: MemorySegment) {
        // The handle is only trusted as an object once AppKit lists it as one of its windows.
        check(isAppWindow(window)) { "the window handle is not one of the app's windows" }
        ObjC.send(window, "setStyleMask:", ObjC.sendLong(window, "styleMask") or FRAME_STYLE)
        ObjC.send(window, "setTitlebarAppearsTransparent:", true)
        ObjC.send(window, "setTitleVisibility:", TITLE_HIDDEN)
        // An empty unified toolbar makes the title bar as tall as the design's, with the lights centered in it.
        val toolbar = ObjC.send(ObjC.send(ObjC.cls("NSToolbar"), "alloc"), "init")
        ObjC.send(window, "setToolbar:", toolbar)
        ObjC.send(toolbar, "release")
        ObjC.send(window, "setToolbarStyle:", TOOLBAR_UNIFIED)
        // Compose drops the shadow of a transparent window; a framed one keeps it.
        ObjC.send(window, "setHasShadow:", true)
        val content = ObjC.send(window, "contentView")
        Arena.ofConfined().use { arena ->
            val bounds = ObjC.sendRect(content, "bounds", arena)
            val effect =
                ObjC.send(
                    ObjC.send(ObjC.cls("NSVisualEffectView"), "alloc"),
                    "initWithFrame:",
                    ObjC.Rect(bounds),
                )
            ObjC.send(effect, "setAutoresizingMask:", FILL_SUPERVIEW)
            ObjC.send(effect, "setBlendingMode:", BEHIND_WINDOW)
            ObjC.send(effect, "setMaterial:", UNDER_WINDOW_BACKGROUND)
            ObjC.send(effect, "setState:", ALWAYS_ACTIVE)
            ObjC.send(ObjC.send(content, "superview"), "addSubview:positioned:relativeTo:", effect, BELOW, content)
            // The superview holds it now.
            ObjC.send(effect, "release")
        }
    }

    private fun isAppWindow(window: MemorySegment): Boolean {
        val app = ObjC.send(ObjC.cls("NSApplication"), "sharedApplication")
        val windows = ObjC.send(app, "windows")
        return (0L until ObjC.sendLong(windows, "count")).any {
            ObjC.send(windows, "objectAtIndex:", it).address() == window.address()
        }
    }

    private fun nsString(text: String): MemorySegment =
        Arena.ofConfined().use { ObjC.send(ObjC.cls("NSString"), "stringWithUTF8String:", it.allocateFrom(text)) }

    // AppKit's constants, from NSWindow.h and NSVisualEffectView.h.
    private const val TITLED = 1L
    private const val CLOSABLE = 2L
    private const val MINIATURIZABLE = 4L
    private const val RESIZABLE = 8L
    private const val FULL_SIZE_CONTENT = 1L shl 15
    private const val FRAME_STYLE = TITLED or CLOSABLE or MINIATURIZABLE or RESIZABLE or FULL_SIZE_CONTENT
    private const val TITLE_HIDDEN = 1L
    private const val TOOLBAR_UNIFIED = 3L
    private const val FILL_SUPERVIEW = 2L or 16L
    private const val BEHIND_WINDOW = 0L
    private const val UNDER_WINDOW_BACKGROUND = 21L
    private const val ALWAYS_ACTIVE = 1L
    private const val BELOW = -1L
}
