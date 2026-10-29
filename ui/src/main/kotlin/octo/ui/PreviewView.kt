package octo.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import octo.ui.preview.Cell
import octo.ui.preview.Output
import octo.ui.preview.Preview
import octo.ui.preview.Table
import octo.ui.vm.PreviewState

private val ColumnWidth = 160.dp

@Composable
internal fun PreviewView(state: PreviewState) {
    when (state) {
        PreviewState.Loading -> Text("Loading preview…")
        is PreviewState.Failed -> Notice(state.message, Tone.Error)
        is PreviewState.Ready -> Ready(state.preview)
    }
}

@Composable
private fun Ready(preview: Preview) {
    when (preview) {
        is Preview.Picture -> {
            Picture(preview.bitmap, Modifier.fillMaxSize())
        }

        is Preview.Notebook -> {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Gap)) {
                items(preview.cells) { NotebookCell(it) }
            }
        }

        is Preview.Archive -> {
            Archive(preview)
        }

        is Preview.Database -> {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(LargeGap)) {
                if (preview.tables.isEmpty()) item { Text("No tables.") }
                items(preview.tables) { DatabaseTable(it) }
            }
        }

        is Preview.Source -> {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(preview.note, style = MaterialTheme.typography.bodySmall)
                SelectionContainer { Text(preview.text, fontFamily = FontFamily.Monospace) }
            }
        }
    }
}

@Composable
private fun Picture(
    bitmap: ImageBitmap,
    modifier: Modifier = Modifier,
) {
    // Inside: shrink a large picture to fit, never blow a small one up.
    Image(bitmap, null, modifier, alignment = Alignment.TopStart, contentScale = ContentScale.Inside)
}

@Composable
private fun NotebookCell(cell: Cell) {
    Column(verticalArrangement = Arrangement.spacedBy(SmallGap)) {
        val code = cell.kind == "code"
        Text(
            cell.source,
            Modifier
                .fillMaxWidth()
                .background(if (code) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
                .padding(Gap),
            fontFamily = if (code) FontFamily.Monospace else FontFamily.Default,
        )
        cell.outputs.forEach { output ->
            when (output) {
                is Output.Text -> {
                    val colors = MaterialTheme.colorScheme
                    Text(
                        output.text,
                        color = if (output.error) colors.error else colors.onSurface,
                        fontFamily = FontFamily.Monospace,
                    )
                }

                is Output.Image -> {
                    Picture(output.bitmap)
                }
            }
        }
    }
}

@Composable
private fun Archive(archive: Preview.Archive) {
    Column(verticalArrangement = Arrangement.spacedBy(SmallGap)) {
        Text("${archive.total} entries", style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.weight(1f, fill = false)) {
            items(archive.entries) { entry ->
                Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
                    Text(entry.name, Modifier.weight(1f), fontFamily = FontFamily.Monospace)
                    if (entry.size >= 0) Text("${entry.size} B", style = MaterialTheme.typography.bodySmall)
                }
            }
            val more = archive.total - archive.entries.size
            if (more > 0) item { Text("… and $more more") }
        }
    }
}

@Composable
private fun DatabaseTable(table: Table) {
    Column(verticalArrangement = Arrangement.spacedBy(SmallGap)) {
        Text(table.name, style = MaterialTheme.typography.titleSmall)
        if (table.rows.isEmpty()) {
            Text("No rows.", style = MaterialTheme.typography.bodySmall)
            return
        }
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            TableRow(table.columns, FontWeight.Bold)
            table.rows.forEach { TableRow(it, FontWeight.Normal) }
        }
    }
}

@Composable
private fun TableRow(
    cells: List<String>,
    weight: FontWeight,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
        cells.forEach {
            Text(
                it,
                Modifier.width(ColumnWidth),
                fontFamily = FontFamily.Monospace,
                fontWeight = weight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
