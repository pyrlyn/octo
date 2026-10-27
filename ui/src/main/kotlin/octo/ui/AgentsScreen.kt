package octo.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import octo.ui.vm.AgentRow
import octo.ui.vm.AgentsViewModel

private val IconSize = 32.dp
private val NameWidth = 200.dp
private val CredentialsWidth = 120.dp
private val ProcessWidth = 110.dp

@Composable
internal fun AgentsScreen(
    vm: AgentsViewModel,
    onClose: () -> Unit,
) {
    val state by vm.state.collectAsState()
    Column(Modifier.fillMaxSize().padding(LargeGap), verticalArrangement = Arrangement.spacedBy(Gap)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Running agents", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Close") }
        }
        state.error?.let { Banner(it, error = true, onDismiss = vm::dismissError) }
        HorizontalDivider()
        if (state.rows.isEmpty()) {
            Text("No agent is running", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        Header()
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Gap)) {
            items(state.rows, key = { it.id.value }) { AgentRowItem(it, onStop = { vm.stop(it.id) }) }
        }
    }
}

@Composable
private fun Header() {
    val style = MaterialTheme.typography.labelMedium
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LargeGap)) {
        Text("Agent", Modifier.width(IconSize + Gap + NameWidth), style = style, color = color)
        Text("Credentials", Modifier.width(CredentialsWidth), style = style, color = color)
        Text("CPU · RAM", Modifier.width(ProcessWidth), style = style, color = color)
        Text("Model · context · tokens", Modifier.weight(1f), style = style, color = color)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AgentRowItem(
    row: AgentRow,
    onStop: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val small = MaterialTheme.typography.bodySmall
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LargeGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.width(IconSize + Gap + NameWidth), verticalAlignment = Alignment.CenterVertically) {
            AgentGlyph(row.icon, row.agent, IconSize, Modifier.padding(end = Gap))
            Column {
                Text(row.agent, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(row.title, style = small, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(row.credentials, Modifier.width(CredentialsWidth), maxLines = 1)
        Column(Modifier.width(ProcessWidth)) {
            Text(row.cpu, maxLines = 1)
            Text(row.memory, style = small, color = muted, maxLines = 1)
        }
        Column(Modifier.weight(1f)) {
            Text(row.model, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Context ${row.context}", style = small, color = muted, maxLines = 1)
            Text(row.tokens, style = small, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TooltipArea(tooltip = { Tip("Stop ${row.agent}") }) {
            IconButton(onClick = onStop, enabled = !row.stopping) {
                Icon(StopIcon, contentDescription = "Stop ${row.agent}", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun Tip(text: String) {
    Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall) {
        Text(
            text,
            Modifier.padding(horizontal = Gap, vertical = SmallGap),
            color = MaterialTheme.colorScheme.inverseOnSurface,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
