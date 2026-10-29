package octo.ui.window

import androidx.compose.ui.awt.ComposeWindow
import octo.core.paths.HostOs

/** The OS settings that change how Octo looks. */
data class OsSettings(
    val reducedTransparency: Boolean = false,
    val reducedMotion: Boolean = false,
)

/** One OS's side of Octo's window: the frame it starts with, its native material and its settings. */
internal interface NativeWindow {
    /** The frame the shell draws once [decorate] has run. */
    val chrome: Chrome

    /** Whether the window starts transparent, so a native material can show through it. */
    val transparent: Boolean

    /**
     * Gives the shown window its native style and material. Returns whether a material now shows
     * through; false keeps the in-app backdrop. Throws when the frame itself could not be restyled.
     */
    suspend fun decorate(window: ComposeWindow): Boolean

    /** Matches the native material to the app's theme. */
    fun setDark(
        window: ComposeWindow,
        dark: Boolean,
    ) {}

    /** Reads the OS settings; it may block, so call it off the UI thread. */
    suspend fun settings(): OsSettings

    companion object {
        fun of(os: HostOs): NativeWindow =
            when (os) {
                HostOs.MacOs -> MacWindow
                HostOs.Windows -> WindowsWindow
                HostOs.Linux -> LinuxWindow
            }
    }
}
