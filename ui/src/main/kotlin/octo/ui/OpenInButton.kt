package octo.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import octo.design.GlassMenuItem
import octo.design.Pyrlyn
import octo.design.SplitButton
import octo.design.Text
import octo.feature.common.AppKind
import octo.feature.common.OpenApp
import octo.ui.vm.OpenInViewModel
import pyrlyn.macos.Icon
import pyrlyn.macos.MacIcon

/**
 * "Open location" with the app it opens, and a menu of the other apps found on this computer. Nothing shows while no
 * local task is selected.
 */
@Composable
internal fun OpenInButton(vm: OpenInViewModel) {
    val state by vm.state.collectAsState()
    val open = state ?: return
    SplitButton(
        onClick = { vm.open(open.main) },
        menuDescription = "Open in",
        menu = { close ->
            open.apps.forEach { app ->
                GlassMenuItem(onClick = {
                    close()
                    vm.open(app)
                }) {
                    AppIcon(app, open.icons[app.id])
                    Text(app.name, maxLines = 1)
                }
            }
        },
    ) {
        AppIcon(open.main, open.icons[open.main.id])
        Text("Open location", maxLines = 1)
    }
}

/** The app's own icon where one was found; a glyph for its kind otherwise, as on Linux. */
@Composable
private fun AppIcon(
    app: OpenApp,
    bitmap: ImageBitmap?,
) {
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = Modifier.size(AppIconSize))
    } else {
        val glyph =
            when (app.kind) {
                AppKind.Files -> MacIcon.Folder
                AppKind.Terminal -> MacIcon.Terminal
                AppKind.Editor -> MacIcon.Document
            }
        Icon(glyph, tint = Pyrlyn.colors.fgMuted, size = AppIconSize)
    }
}

private val AppIconSize = 16.dp
