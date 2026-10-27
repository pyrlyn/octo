package octo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import octo.ui.vm.MainUiState
import octo.ui.vm.MainViewModel
import octo.ui.vm.TaskRow

@Composable
internal fun Sidebar(
    state: MainUiState,
    vm: MainViewModel,
    modifier: Modifier = Modifier,
    /** Null hides the action, while remote access is off. */
    onDevices: (() -> Unit)? = null,
) {
    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainer)) {
        Button(onClick = vm::openNewTask, modifier = Modifier.fillMaxWidth().padding(Gap)) { Text("New task") }
        LazyColumn(Modifier.weight(1f)) {
            state.groups.forEach { group ->
                item(key = "repo:${group.repo}") {
                    Row(
                        Modifier.padding(horizontal = Gap, vertical = SmallGap),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        group.status?.let { StatusDot(it, Modifier.padding(end = Gap)) }
                        Text(
                            group.name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                items(group.tasks, key = { it.id.value }) { row ->
                    Column {
                        row.sectionTitle?.let {
                            Text(
                                it,
                                Modifier.padding(start = Gap, end = Gap, top = Gap),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        row.groupTitle?.let {
                            Text(
                                it,
                                Modifier.padding(start = LargeGap, end = Gap, top = SmallGap),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TaskRowItem(row, selected = row.id == state.selected, onClick = { vm.select(row.id) })
                    }
                }
            }
        }
        TextButton(onClick = vm::openAgents, modifier = Modifier.fillMaxWidth().padding(horizontal = Gap)) {
            Text("Agents")
        }
        onDevices?.let { TextButton(onClick = it, modifier = Modifier.fillMaxWidth().padding(Gap)) { Text("Devices") } }
    }
}

@Composable
private fun TaskRowItem(
    row: TaskRow,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Row(
        Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(onClick = onClick)
            .padding(
                start = LargeGap * (1 + row.depth.coerceAtMost(MAX_FORK_INDENT)),
                end = Gap,
                top = SmallGap,
                bottom = SmallGap,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(row.status, Modifier.padding(end = Gap))
        Column(Modifier.weight(1f)) {
            Text(row.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${row.agent.value} · ${row.status.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** Deeper forks stop moving right, so a long chain still leaves room for the title. */
private const val MAX_FORK_INDENT = 3
