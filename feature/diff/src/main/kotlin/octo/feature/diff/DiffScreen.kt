package octo.feature.diff

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import octo.core.git.FileChange
import octo.core.git.FileDiff
import octo.core.git.Hunk
import octo.core.git.LineKind
import octo.design.EmptyState
import octo.design.Gap
import octo.design.GhostButton
import octo.design.LargeGap
import octo.design.Notice
import octo.design.PaneHeader
import octo.design.PanePadding
import octo.design.Pyrlyn
import octo.design.SecondaryButton
import octo.design.SmallGap
import octo.design.Text
import octo.design.Tone
import octo.design.addedLine
import octo.design.removedLine

@Composable
fun DiffScreen(
    vm: DiffViewModel,
    onClose: () -> Unit,
) {
    val state by vm.state.collectAsState()
    Column(Modifier.fillMaxSize()) {
        val subtitle =
            state.diff?.let {
                val count = state.files.size
                "against ${it.baseRef} · ${if (count == 1) "1 file" else "$count files"}"
            }
        PaneHeader("Diff", subtitle) {
            SecondaryButton(onClick = vm::refresh, enabled = !state.loading) { Text("↻  Refresh") }
            GhostButton(onClick = onClose) { Text("Back to task") }
        }
        Body(state, vm)
    }
}

@Composable
private fun Body(
    state: DiffUiState,
    vm: DiffViewModel,
) {
    when {
        state.error != null -> {
            Column(Modifier.padding(PanePadding)) { Notice(state.error, Tone.Error) }
        }

        state.diff == null -> {
            Text("Loading…", Modifier.padding(PanePadding), color = Pyrlyn.colors.fgMuted)
        }

        state.files.isEmpty() -> {
            EmptyState("±", "No changes yet", "The diff fills in once the agent edits files in its worktree.")
        }

        else -> {
            Row(Modifier.fillMaxSize()) {
                LazyColumn(
                    Modifier.width(FileListWidth).fillMaxHeight().padding(Gap),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(state.files, key = { it.path }) { file ->
                        FileRow(file, selected = file.path == state.selectedPath, onClick = { vm.select(file.path) })
                    }
                }
                Box(Modifier.width(0.5.dp).fillMaxHeight().background(Pyrlyn.colors.glassEdge))
                state.selected?.let {
                    FileView(it, state.preview, state.showDiff, vm::toggleDiff, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FileRow(
    file: FileDiff,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = Pyrlyn.colors
    val shape = Pyrlyn.controlShape
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) c.stateSelected else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = Gap, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(Gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(file.change.marker, style = Pyrlyn.type.mono, color = c.fgSubtle)
        Text(
            file.path,
            Modifier.weight(1f),
            style = Pyrlyn.type.mono,
            color = if (selected) c.stateSelectedFg else c.fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (file.binary) {
            Text("binary", style = Pyrlyn.type.small, color = c.fgSubtle)
        } else {
            Text("+${file.additions} −${file.deletions}", style = Pyrlyn.type.small, color = c.fgMuted)
        }
    }
}

private val FileChange.marker: String
    get() =
        when (this) {
            FileChange.Added -> "A"
            FileChange.Deleted -> "D"
            FileChange.Modified -> "M"
            FileChange.Renamed -> "R"
        }

@Composable
private fun FileView(
    file: FileDiff,
    preview: PreviewState?,
    showDiff: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier) {
        Row(
            Modifier.padding(horizontal = LargeGap, vertical = Gap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Gap),
        ) {
            Text(
                file.oldPath?.let { "$it → ${file.path}" } ?: file.path,
                Modifier.weight(1f),
                style = Pyrlyn.type.mono,
                color = Pyrlyn.colors.fgMuted,
            )
            if (preview != null) SecondaryButton(onClick = onToggle) { Text(if (showDiff) "Preview" else "Diff") }
        }
        when {
            preview != null && !showDiff -> PreviewView(preview)
            file.binary -> Text("Binary file", Modifier.padding(LargeGap), color = Pyrlyn.colors.fgMuted)
            else -> Hunks(file)
        }
    }
}

@Composable
private fun Hunks(file: FileDiff) {
    SelectionContainer {
        LazyColumn(Modifier.fillMaxSize().background(Pyrlyn.colors.bgSunken)) {
            file.hunks.forEach { hunk ->
                item {
                    Text(
                        hunk.header,
                        Modifier.fillMaxWidth().padding(horizontal = LargeGap, vertical = SmallGap),
                        style = Pyrlyn.type.mono,
                        color = Pyrlyn.colors.accentFg,
                    )
                }
                items(hunk.lines) { line ->
                    val (prefix, color) =
                        when (line.kind) {
                            LineKind.Added -> "+" to addedLine()
                            LineKind.Removed -> "-" to removedLine()
                            LineKind.Context -> " " to Color.Transparent
                        }
                    Text(
                        "$prefix  ${line.text}",
                        Modifier.fillMaxWidth().background(color).padding(horizontal = LargeGap),
                        style = Pyrlyn.type.mono,
                    )
                }
            }
        }
    }
}

private val Hunk.header: String get() = "@@ -$oldStart,$oldCount +$newStart,$newCount @@ $section"

private val FileListWidth = 280.dp
