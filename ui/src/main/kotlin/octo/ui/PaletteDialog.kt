package octo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import octo.ui.vm.PaletteItem
import octo.ui.vm.PaletteViewModel

/** Cmd+K on macOS, Ctrl+K elsewhere, as each platform's apps use it. */
fun isPaletteShortcut(event: KeyEvent): Boolean =
    event.type == KeyEventType.KeyDown &&
        event.key == Key.K &&
        if (MAC) event.isMetaPressed else event.isCtrlPressed

private val MAC = System.getProperty("os.name").orEmpty().startsWith("Mac")

/** Typing filters; arrows move the highlight, Enter runs it and Escape closes, so no mouse is needed. */
@Composable
internal fun PaletteDialog(
    vm: PaletteViewModel,
    onDismiss: () -> Unit,
    onChoose: (PaletteItem) -> Unit,
) {
    val state by vm.state.collectAsState()
    val focus = remember { FocusRequester() }
    val list = rememberLazyListState()
    LaunchedEffect(state.selected) { if (state.items.isNotEmpty()) list.animateScrollToItem(state.selected) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.width(PaletteWidth)) {
            Column(Modifier.padding(Gap)) {
                OutlinedTextField(
                    state.query,
                    vm::edit,
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.DirectionDown -> vm.move(1)
                                Key.DirectionUp -> vm.move(-1)
                                Key.Enter, Key.NumPadEnter -> vm.chosen()?.let(onChoose)
                                Key.Escape -> onDismiss()
                                else -> return@onPreviewKeyEvent false
                            }
                            true
                        },
                    placeholder = { Text("Search tasks and messages, or run a command") },
                    singleLine = true,
                )
                LazyColumn(Modifier.heightIn(max = PaletteHeight), state = list) {
                    itemsIndexed(state.items) { i, item ->
                        PaletteRow(item, highlighted = i == state.selected, onClick = { onChoose(item) })
                    }
                }
                if (state.items.isEmpty()) Text("Nothing matches", Modifier.padding(Gap))
            }
        }
    }
    DisposableEffect(Unit) {
        focus.requestFocus()
        onDispose {}
    }
}

@Composable
private fun PaletteRow(
    item: PaletteItem,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val background = if (highlighted) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Column(
        Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = Gap, vertical = SmallGap),
    ) {
        when (item) {
            is PaletteItem.Open -> {
                Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                item.snippet?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            is PaletteItem.Run -> {
                Text(item.command.label, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private val PaletteWidth = 560.dp
private val PaletteHeight = 360.dp
