package octo.ui.window

import androidx.compose.ui.awt.ComposeWindow
import kotlinx.coroutines.withTimeoutOrNull
import octo.core.process.JvmProcessLauncher
import octo.core.process.ProcessSpec
import octo.core.process.run
import kotlin.time.Duration.Companion.seconds

/**
 * Linux: a header bar drawn by the shell and no native material, since compositor blur differs
 * from one desktop to the next. GNOME's animation switch stands for reduced motion; there is no
 * desktop-wide reduced transparency.
 */
internal object LinuxWindow : NativeWindow {
    override val chrome = Chrome.HeaderBar
    override val transparent = false

    override suspend fun decorate(window: ComposeWindow) = false

    override suspend fun settings(): OsSettings {
        val animations =
            withTimeoutOrNull(GSETTINGS_TIMEOUT) {
                JvmProcessLauncher().run(
                    ProcessSpec("gsettings", listOf("get", "org.gnome.desktop.interface", "enable-animations")),
                )
            }
        return OsSettings(reducedMotion = animations?.exitCode == 0 && animationsOff(animations.stdout))
    }

    private val GSETTINGS_TIMEOUT = 2.seconds
}

/** Reads `gsettings get org.gnome.desktop.interface enable-animations`. */
internal fun animationsOff(output: String) = output.trim() == "false"
