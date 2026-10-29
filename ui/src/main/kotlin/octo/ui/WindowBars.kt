package octo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import octo.design.CaptionButtons
import octo.design.CloseCircle
import octo.design.Gap
import octo.design.GhostButton
import octo.design.GlassMenu
import octo.design.GlassMenuItem
import octo.design.Pyrlyn
import octo.design.SecondaryButton
import octo.design.SmallGap
import octo.design.Text
import octo.design.chromeVeil
import octo.ui.vm.MainUiState
import octo.ui.vm.ViewModels
import octo.ui.window.WindowFrame

/** Windows: the app's mark and name, then minimize, maximize and close. */
@Composable
internal fun CaptionBar(frame: WindowFrame) {
    Row(Modifier.fillMaxWidth().height(CaptionHeight)) {
        frame.dragArea(Modifier.weight(1f).fillMaxHeight()) {
            Row(
                Modifier.fillMaxSize().padding(start = CaptionInset),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Gap),
            ) {
                OctoMark(CaptionMarkSize)
                Text("Octo", style = Pyrlyn.type.small)
            }
        }
        CaptionButtons(frame.maximized, frame.onMinimize, frame.onToggleMaximize, frame.onClose)
    }
}

/** Linux, as a libadwaita header bar: new task, the title centered, search, the app menu and close. */
@Composable
internal fun HeaderBar(
    frame: WindowFrame,
    state: MainUiState,
    models: ViewModels,
    onSearch: (() -> Unit)?,
) {
    val title =
        state.groups
            .flatMap { it.tasks }
            .firstOrNull { it.id == state.selected }
            ?.title
    frame.dragArea(Modifier.fillMaxWidth().height(HeaderBarHeight).chromeVeil()) {
        Box(Modifier.fillMaxSize().padding(horizontal = Gap)) {
            SecondaryButton(
                onClick = { models.main.openNewTask() },
                Modifier.align(Alignment.CenterStart),
            ) { Text("+") }
            Row(
                Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SmallGap),
            ) {
                Text("Octo", fontWeight = FontWeight.Bold)
                title?.let { Text("— $it", color = Pyrlyn.colors.fgMuted, maxLines = 1) }
            }
            Row(
                Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Gap),
            ) {
                if (onSearch != null) SearchField(onSearch, HeaderSearchWidth, "Search")
                AppMenu(models)
                CloseCircle(frame.onClose)
            }
        }
    }
}

/** The header bar's primary menu: the panes the sidebar's footer also opens. */
@Composable
private fun AppMenu(models: ViewModels) {
    val main = models.main
    var open by remember { mutableStateOf(false) }
    val items =
        listOfNotNull<Pair<String, () -> Unit>>(
            "Agents" to main::openAgents,
            ("Usage" to main::openUsage).takeIf { models.canShowUsage },
            ("Devices" to main::openDevices).takeIf { models.canPair },
            ("Settings" to { main.showSettings(true) }).takeIf { models.canEditSettings },
        )
    Box {
        GhostButton(onClick = { open = true }) { Text("☰") }
        GlassMenu(open, onDismissRequest = { open = false }) {
            items.forEach { (label, action) ->
                GlassMenuItem(onClick = {
                    open = false
                    action()
                }) { Text(label) }
            }
        }
    }
}

private val CaptionHeight = 32.dp
private val CaptionInset = 10.dp
private val CaptionMarkSize = 16.dp
private val HeaderBarHeight = 46.dp
private val HeaderSearchWidth = 260.dp
