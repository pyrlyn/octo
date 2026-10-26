package octo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import octo.core.git.FileChange
import octo.core.git.FileDiff
import octo.core.git.Hunk
import octo.core.git.LineKind
import octo.ui.vm.DiffUiState
import octo.ui.vm.DiffViewModel

@Composable
internal fun DiffScreen(
    vm: DiffViewModel,
    onClose: () -> Unit,
) {
    val state by vm.state.collectAsState()
    Column(Modifier.fillMaxSize().padding(LargeGap), verticalArrangement = Arrangement.spacedBy(Gap)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Gap)) {
            Text(
                state.diff?.let { "Changes against ${it.baseRef}" } ?: "Changes",
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedButton(onClick = vm::refresh, enabled = !state.loading) { Text("Refresh") }
            OutlinedButton(onClick = onClose) { Text("Back to task") }
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
            Text(state.error, color = MaterialTheme.colorScheme.error)
        }

        state.diff == null -> {
            Text("Loading…")
        }

        state.files.isEmpty() -> {
            Text("No changes yet.")
        }

        else -> {
            Row(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.width(FileListWidth).fillMaxHeight()) {
                    items(state.files, key = { it.path }) { file ->
                        FileRow(file, selected = file.path == state.selectedPath, onClick = { vm.select(file.path) })
                    }
                }
                VerticalDivider()
                state.selected?.let { FileView(it, Modifier.weight(1f).padding(start = Gap)) }
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
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Row(
        Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(onClick = onClick)
            .padding(SmallGap),
        horizontalArrangement = Arrangement.spacedBy(Gap),
    ) {
        Text(file.change.marker, fontFamily = FontFamily.Monospace)
        Text(file.path, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("+${file.additions} -${file.deletions}", style = MaterialTheme.typography.bodySmall)
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
    modifier: Modifier,
) {
    Column(modifier) {
        Text(file.oldPath?.let { "$it → ${file.path}" } ?: file.path, style = MaterialTheme.typography.titleSmall)
        if (file.binary) {
            Text("Binary file")
            return
        }
        SelectionContainer {
            LazyColumn {
                file.hunks.forEach { hunk ->
                    item {
                        Text(
                            hunk.header,
                            Modifier.padding(top = Gap),
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    items(hunk.lines) { line ->
                        val (prefix, color) =
                            when (line.kind) {
                                LineKind.Added -> "+" to AddedLine
                                LineKind.Removed -> "-" to RemovedLine
                                LineKind.Context -> " " to Color.Transparent
                            }
                        Text(
                            prefix + line.text,
                            Modifier.fillMaxWidth().background(color),
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

private val Hunk.header: String get() = "@@ -$oldStart,$oldCount +$newStart,$newCount @@ $section"
