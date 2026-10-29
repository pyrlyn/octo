package octo.ui.window

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowDecoration
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import octo.core.paths.HostOs
import octo.core.paths.currentHostOs

/**
 * Octo's main window: undecorated, with the frame drawn by the shell as the design has it for
 * each OS, a native material where the OS has one, and the OS's reduced transparency and motion.
 */
@OptIn(ExperimentalComposeUiApi::class) // Undecorated with a resizer is still experimental in Compose.
@Composable
fun ApplicationScope.OctoWindow(
    title: String,
    onCloseRequest: () -> Unit,
    onPreviewKeyEvent: (KeyEvent) -> Boolean,
    os: HostOs = currentHostOs(),
    content: @Composable FrameWindowScope.(WindowFrame, SystemLook) -> Unit,
) {
    val native = remember(os) { NativeWindow.of(os) }
    val state = rememberWindowState()
    Window(
        onCloseRequest = onCloseRequest,
        state = state,
        title = title,
        // macOS resizes from its own frame once the title bar is back.
        decoration = WindowDecoration.Undecorated(if (os == HostOs.MacOs) 0.dp else RESIZER),
        transparent = native.transparent,
        onPreviewKeyEvent = onPreviewKeyEvent,
    ) {
        var chrome by remember { mutableStateOf(native.chrome) }
        var material by remember { mutableStateOf(false) }
        var settings by remember { mutableStateOf(OsSettings()) }
        LaunchedEffect(window) {
            material =
                failOpen("native window frame") { native.decorate(window) }
                    ?: run {
                        // Without its title bar back the window has no buttons, so the shell draws them.
                        chrome = Chrome.Caption
                        false
                    }
        }
        // Settings can change while Octo runs; coming back to the window is when they matter.
        val focused = LocalWindowInfo.current.isWindowFocused
        LaunchedEffect(focused) {
            if (focused) {
                failOpen(
                    "OS look settings",
                ) { withContext(Dispatchers.IO) { native.settings() } }?.let { settings = it }
            }
        }
        val maximized = state.placement == WindowPlacement.Maximized
        val toggle = {
            state.placement = if (maximized) WindowPlacement.Floating else WindowPlacement.Maximized
        }
        val frame =
            WindowFrame(
                os = os,
                chrome = chrome,
                maximized = maximized,
                onMinimize = { state.isMinimized = true },
                onToggleMaximize = toggle,
                onClose = onCloseRequest,
                onDarkChanged = { dark ->
                    runCatching { native.setDark(window, dark) }.onFailure { warn("window theme", it) }
                },
                dragArea = { modifier, inner ->
                    WindowDraggableArea(
                        modifier.pointerInput(toggle) { detectTapGestures(onDoubleTap = { toggle() }) },
                    ) {
                        inner()
                    }
                },
            )
        content(frame, SystemLook(material, settings.reducedTransparency, settings.reducedMotion))
    }
}

/** Runs native window code; a failure is reported and yields null, never an error. */
@Suppress("TooGenericExceptionCaught")
private suspend fun <T> failOpen(
    what: String,
    block: suspend () -> T,
): T? =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        warn(what, e)
        null
    }

private val RESIZER = 6.dp
