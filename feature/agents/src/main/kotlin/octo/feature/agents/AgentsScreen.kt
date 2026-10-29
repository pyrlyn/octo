package octo.feature.agents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import octo.design.Banner
import octo.design.Dot
import octo.design.EmptyState
import octo.design.Gap
import octo.design.GhostButton
import octo.design.Glyph
import octo.design.LargeGap
import octo.design.Meter
import octo.design.PaneHeader
import octo.design.Pyrlyn
import octo.design.SecondaryButton
import octo.design.TableHeader
import octo.design.TableRow
import octo.design.Text
import octo.feature.common.authColor

private val IconSize = 22.dp
private val NameWidth = 220.dp
private val CredentialsWidth = 130.dp
private val ProcessWidth = 120.dp

@Composable
fun AgentsScreen(
    vm: AgentsViewModel,
    onClose: () -> Unit,
) {
    val state by vm.state.collectAsState()
    Column(Modifier.fillMaxSize()) {
        val count = state.rows.size
        PaneHeader("Running agents", if (count == 1) "1 agent" else "$count agents") {
            GhostButton(onClick = onClose) { Text("Close") }
        }
        state.error?.let {
            Column(Modifier.padding(LargeGap)) { Banner(it, error = true, onDismiss = vm::dismissError) }
        }
        if (state.rows.isEmpty()) {
            EmptyState("◇", "No agent is running", "Agents show up here while a task has one at work.")
            return@Column
        }
        TableHeader {
            Text("AGENT", Modifier.width(NameWidth))
            Text("CREDENTIALS", Modifier.width(CredentialsWidth))
            Text("CPU · RAM", Modifier.width(ProcessWidth))
            Text("MODEL · CONTEXT · TOKENS", Modifier.weight(1f))
        }
        LazyColumn(Modifier.weight(1f)) {
            items(state.rows, key = { it.id.value }) { AgentRowItem(it, onStop = { vm.stop(it.id) }) }
        }
    }
}

@Composable
private fun AgentRowItem(
    row: AgentRow,
    onStop: () -> Unit,
) {
    val c = Pyrlyn.colors
    val small = Pyrlyn.type.small
    TableRow {
        Row(Modifier.width(NameWidth), verticalAlignment = Alignment.CenterVertically) {
            Glyph(row.icon?.svg, row.agent, IconSize, Modifier.padding(end = Gap))
            Column {
                Text(row.agent, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(row.title, style = small, color = c.fgMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(
            Modifier.width(CredentialsWidth),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Dot(authColor(row.auth))
            Text(row.credentials, style = small, color = c.fgMuted, maxLines = 1)
        }
        Text(
            "${row.cpu} · ${row.memory}",
            Modifier.width(ProcessWidth),
            style = Pyrlyn.type.mono,
            color = c.fgMuted,
            maxLines = 1,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("${row.model} · ${row.tokens}", style = small, maxLines = 1, overflow = TextOverflow.Ellipsis)
            row.contextShare?.let { Meter(it, Modifier.width(MeterWidth)) }
            Text("Context ${row.context}", style = small, color = c.fgMuted, maxLines = 1)
        }
        SecondaryButton(onClick = onStop, enabled = !row.stopping) {
            Text(if (row.stopping) "Stopping" else "Stop")
        }
    }
}

private val MeterWidth = 220.dp
