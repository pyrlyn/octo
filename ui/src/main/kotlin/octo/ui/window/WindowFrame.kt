package octo.ui.window

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import octo.core.paths.HostOs
import octo.core.paths.currentHostOs

/** How much of the window frame the shell draws. */
enum class Chrome {
    /** The system draws the whole frame, as in tests and renders. */
    System,

    /** macOS keeps its own traffic lights over the content; the shell leaves room for them. */
    MacOs,

    /** Windows: a caption bar with minimize, maximize and close. */
    Caption,

    /** Linux: a header bar with new task, title, search, menu and close. */
    HeaderBar,
}

/** What the OS asks of the look. */
data class SystemLook(
    /** A native material (vibrancy, Mica) shows through the window, so no backdrop is drawn in it. */
    val material: Boolean = false,
    val reducedTransparency: Boolean = false,
    val reducedMotion: Boolean = false,
)

/** The window the shell draws its frame for. */
class WindowFrame(
    /** Whose look the shell follows; renders pick another OS to show its recipe. */
    val os: HostOs = currentHostOs(),
    val chrome: Chrome = Chrome.System,
    val maximized: Boolean = false,
    val onMinimize: () -> Unit = {},
    val onToggleMaximize: () -> Unit = {},
    val onClose: () -> Unit = {},
    /** Tells the native material whether the app is dark, since that can differ from the OS. */
    val onDarkChanged: (Boolean) -> Unit = {},
    /** Wraps a part of the frame that moves the window when dragged. */
    val dragArea: @Composable (Modifier, @Composable () -> Unit) -> Unit = { modifier, content ->
        Box(modifier) { content() }
    },
)
