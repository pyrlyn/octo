package octo.feature.palette

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import octo.design.Elevation
import octo.design.Gap
import octo.design.GlassField
import octo.design.GlassLevel
import octo.design.Kbd
import octo.design.LargeGap
import octo.design.LocalContentColor
import octo.design.Pyrlyn
import octo.design.SectionLabel
import octo.design.SmallGap
import octo.design.Text
import octo.design.glass
import octo.feature.common.label

/** Cmd+K on macOS, Ctrl+K elsewhere, as each platform's apps use it. */
fun isPaletteShortcut(event: KeyEvent): Boolean =
    event.type == KeyEventType.KeyDown &&
        event.key == Key.K &&
        if (MAC) event.isMetaPressed else event.isCtrlPressed

private val MAC = System.getProperty("os.name").orEmpty().startsWith("Mac")

/** Typing filters; arrows move the highlight, Enter runs it and Escape closes, so no mouse is needed. */
@Composable
fun PaletteDialog(
    vm: PaletteViewModel,
    onDismiss: () -> Unit,
    onChoose: (PaletteItem) -> Unit,
) {
    val state by vm.state.collectAsState()
    val focus = remember { FocusRequester() }
    val list = rememberLazyListState()
    LaunchedEffect(state.selected) { if (state.items.isNotEmpty()) list.animateScrollToItem(state.selected) }
    // Not centred: the palette opens near the top, where the eye already is after the shortcut.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier
                    .padding(top = PaletteTop)
                    .width(PaletteWidth)
                    .glass(RoundedCornerShape(Pyrlyn.platform.radiusWindow), GlassLevel.Floating, Elevation.E3)
                    // Swallows clicks so only the space around the panel closes it.
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .padding(Gap),
                verticalArrangement = Arrangement.spacedBy(SmallGap),
            ) {
                GlassField(
                    state.query,
                    vm::edit,
                    Modifier.fillMaxWidth().focusRequester(focus).paletteKeys(vm, onDismiss, onChoose),
                    placeholder = { Text("Search tasks and messages, or run a command") },
                    trailingIcon = { Kbd("esc") },
                    singleLine = true,
                )
                LazyColumn(Modifier.heightIn(max = PaletteHeight), state = list) {
                    itemsIndexed(state.items) { i, item ->
                        val section = item.section
                        if (section != state.items.getOrNull(i - 1)?.section) {
                            SectionLabel(section, Modifier.padding(start = Gap, top = Gap, bottom = SmallGap))
                        }
                        PaletteRow(item, highlighted = i == state.selected, onClick = { onChoose(item) })
                    }
                }
                if (state.items.isEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = LargeGap),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Nothing matches", fontWeight = FontWeight.Medium)
                        Text("Try a task title, a word from a message or a command", color = Pyrlyn.colors.fgMuted)
                    }
                }
            }
        }
    }
    DisposableEffect(Unit) {
        focus.requestFocus()
        onDispose {}
    }
}

/** Arrows move the highlight, Enter runs it, Escape closes; everything else goes to the field. */
private fun Modifier.paletteKeys(
    vm: PaletteViewModel,
    onDismiss: () -> Unit,
    onChoose: (PaletteItem) -> Unit,
): Modifier =
    onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.DirectionDown -> vm.move(1)
            Key.DirectionUp -> vm.move(-1)
            Key.Enter, Key.NumPadEnter -> vm.chosen()?.let(onChoose)
            Key.Escape -> onDismiss()
            else -> return@onPreviewKeyEvent false
        }
        true
    }

private val PaletteItem.section: String
    get() =
        when (this) {
            is PaletteItem.Open -> if (snippet == null) "Tasks" else "Messages"
            is PaletteItem.Run -> "Commands"
        }

@Composable
private fun PaletteRow(
    item: PaletteItem,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val c = Pyrlyn.colors
    val muted = if (highlighted) c.onAccent else c.fgMuted
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Pyrlyn.controlShape)
            .background(if (highlighted) c.accentSolid else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides if (highlighted) c.onAccent else c.fg) {
            Column(Modifier.weight(1f)) {
                when (item) {
                    is PaletteItem.Open -> {
                        Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        item.snippet?.let {
                            Text(
                                "“$it”",
                                style = Pyrlyn.type.small,
                                color = muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    is PaletteItem.Run -> {
                        Text(item.command.label)
                    }
                }
            }
            if (highlighted) Text("↵", color = muted)
        }
    }
}

private val PaletteWidth = 640.dp
private val PaletteTop = 96.dp
private val PaletteHeight = 360.dp
