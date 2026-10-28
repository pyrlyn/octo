package octo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import octo.ui.vm.UsagePeriod
import octo.ui.vm.UsageRow
import octo.ui.vm.UsageViewModel

private val IconSize = 32.dp
private val NameWidth = 240.dp
private val SourceWidth = 120.dp
private val CountWidth = 90.dp

@Composable
internal fun UsageScreen(
    vm: UsageViewModel,
    onClose: () -> Unit,
) {
    val state by vm.state.collectAsState()
    Column(Modifier.fillMaxSize().padding(LargeGap), verticalArrangement = Arrangement.spacedBy(Gap)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Usage", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = vm::refresh, enabled = !state.loading) { Text("Refresh") }
            TextButton(onClick = onClose) { Text("Close") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Gap), verticalAlignment = Alignment.CenterVertically) {
            UsagePeriod.entries.forEach { period ->
                FilterChip(
                    selected = period == state.period,
                    onClick = { vm.choose(period) },
                    label = { Text(period.label) },
                )
            }
            Text(
                if (state.loading) "Reading history…" else "${state.total} tokens",
                Modifier.weight(1f),
                textAlign = TextAlign.End,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.error?.let { Banner(it, error = true, onDismiss = vm::dismissError) }
        state.warnings.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
        HorizontalDivider()
        if (state.rows.isEmpty()) {
            if (!state.loading) Text("No usage in this period", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        Header()
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Gap)) {
            items(state.rows) { UsageRowItem(it) }
        }
    }
}

@Composable
private fun Header() {
    val style = MaterialTheme.typography.labelMedium
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LargeGap)) {
        Text("Agent · model", Modifier.width(IconSize + Gap + NameWidth), style = style, color = color)
        Text("Counted from", Modifier.width(SourceWidth), style = style, color = color)
        listOf("Input", "Output", "Cache read", "Cache write", "Total").forEach {
            Text(it, Modifier.width(CountWidth), style = style, color = color, textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun UsageRowItem(row: UsageRow) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LargeGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.width(IconSize + Gap + NameWidth), verticalAlignment = Alignment.CenterVertically) {
            AgentGlyph(row.icon, row.agent, IconSize, Modifier.padding(end = Gap))
            Column {
                Text(row.agent, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    row.model,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(row.source, Modifier.width(SourceWidth), maxLines = 1)
        listOf(row.input, row.output, row.cacheRead, row.cacheWrite).forEach {
            Text(it, Modifier.width(CountWidth), maxLines = 1, textAlign = TextAlign.End)
        }
        Text(
            row.total,
            Modifier.width(CountWidth),
            maxLines = 1,
            textAlign = TextAlign.End,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
