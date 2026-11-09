// The kit's controls drawn with the macOS library, kept apart from the Glass ones they stand in for.
package octo.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pyrlyn.macos.ButtonSize
import pyrlyn.macos.ButtonStyle
import pyrlyn.macos.LocalMacContentColor
import pyrlyn.macos.LocalMacTextStyle
import pyrlyn.macos.Mac
import pyrlyn.macos.MacIcon
import pyrlyn.macos.Sheet
import pyrlyn.macos.SheetButtons
import pyrlyn.macos.Button as MacButton
import pyrlyn.macos.Tone as MacTone

/** A kit button drawn by `pyrlyn.macos`, its content in Octo's own text locals. */
@Composable
internal fun MacKitButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    style: ButtonStyle,
    ink: Color?,
    content: @Composable RowScope.() -> Unit,
) {
    MacButton(onClick, modifier, style, ButtonSize.Regular, enabled) {
        ProvideContent(LocalMacTextStyle.current, ink ?: LocalMacContentColor.current) { content() }
    }
}

/** The macOS sheet: the title and its text, then the buttons at the trailing edge, on the elevated surface. */
@Composable
internal fun MacDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier,
    dismissButton: (@Composable () -> Unit)?,
    title: (@Composable () -> Unit)?,
    text: (@Composable () -> Unit)?,
) {
    val m = Mac.type
    Dialog(onDismissRequest = onDismissRequest, DialogProperties(usePlatformDefaultWidth = false)) {
        Sheet(modifier.widthIn(max = DialogWidth)) {
            title?.let { ProvideContent(m.title, Pyrlyn.colors.fg, it) }
            text?.let {
                ProvideContent(m.body, Pyrlyn.colors.fg) {
                    Box(Modifier.weight(1f, fill = false)) { it() }
                }
            }
            SheetButtons {
                dismissButton?.invoke()
                confirmButton()
            }
        }
    }
}

internal fun Tone.mac(): MacTone =
    when (this) {
        Tone.Info -> MacTone.Info
        Tone.Warning -> MacTone.Warning
        Tone.Error -> MacTone.Error
    }

internal fun Tone.icon(): MacIcon =
    when (this) {
        Tone.Info -> MacIcon.Document
        Tone.Warning, Tone.Error -> MacIcon.Shield
    }
