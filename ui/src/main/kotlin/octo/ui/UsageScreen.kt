package octo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import octo.ui.theme.Pyrlyn
import octo.ui.vm.UsagePeriod
import octo.ui.vm.UsageRow
import octo.ui.vm.UsageViewModel

private val IconSize = 22.dp
private val NameWidth = 280.dp
private val SourceWidth = 130.dp
private val CountWidth = 90.dp
private val ChartHeight = 76.dp
private val BarRadius = 3.dp
private val BarGap = 4.dp

@Composable
internal fun UsageScreen(
    vm: UsageViewModel,
    onClose: () -> Unit,
) {
    val state by vm.state.collectAsState()
    Column(Modifier.fillMaxSize()) {
        val subtitle = if (state.loading) "Reading history…" else "${state.period.label} · ${state.total} tokens"
        PaneHeader("Usage", subtitle) {
            SecondaryButton(onClick = vm::refresh, enabled = !state.loading) { Text("↻  Refresh") }
            GhostButton(onClick = onClose) { Text("Close") }
        }
        Column(
            Modifier.padding(horizontal = PanePadding, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(Gap),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(SmallGap)) {
                UsagePeriod.entries.forEach { period ->
                    GhostButton(onClick = { vm.choose(period) }, selected = period == state.period) {
                        Text(period.label)
                    }
                }
            }
            state.error?.let { Banner(it, error = true, onDismiss = vm::dismissError) }
            state.warnings.forEach { Notice(it, Tone.Warning) }
            if (state.chart.any { it > 0 }) Chart(state.chart)
        }
        if (state.rows.isEmpty()) {
            if (!state.loading) {
                EmptyState("∑", "No usage in this period", "Token counts appear once an agent has run.")
            }
            return@Column
        }
        TableHeader {
            Text("AGENT · MODEL", Modifier.width(NameWidth))
            Text("COUNTED FROM", Modifier.width(SourceWidth))
            listOf("INPUT", "OUTPUT", "CACHE READ", "CACHE WRITE", "TOTAL").forEach {
                Text(it, Modifier.width(CountWidth), textAlign = TextAlign.End)
            }
        }
        LazyColumn(Modifier.weight(1f)) { items(state.rows) { UsageRowItem(it) } }
    }
}

/** One bar per hour or day, scaled to the busiest; empty buckets leave a gap so the time axis stays even. */
@Composable
private fun Chart(bars: List<Long>) {
    val c = Pyrlyn.colors
    val most = bars.max().toFloat()
    val shape = RoundedCornerShape(topStart = BarRadius, topEnd = BarRadius)
    val fill = Brush.verticalGradient(listOf(c.accentSolidHover, c.accentSolid))
    Row(
        Modifier.fillMaxWidth().height(ChartHeight),
        horizontalArrangement = Arrangement.spacedBy(BarGap),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEach { tokens ->
            Box(Modifier.weight(1f).fillMaxHeight(tokens / most).background(fill, shape))
        }
    }
}

@Composable
private fun UsageRowItem(row: UsageRow) {
    val c = Pyrlyn.colors
    val mono = Pyrlyn.type.mono
    TableRow {
        Row(Modifier.width(NameWidth), verticalAlignment = Alignment.CenterVertically) {
            AgentGlyph(row.icon, row.agent, IconSize, Modifier.padding(end = Gap))
            Text(
                "${row.agent} · ${row.model}",
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(row.source, Modifier.width(SourceWidth), style = Pyrlyn.type.small, color = c.fgMuted, maxLines = 1)
        listOf(row.input, row.output, row.cacheRead, row.cacheWrite).forEach {
            Text(
                it,
                Modifier.width(CountWidth),
                style = mono,
                color = c.fgMuted,
                maxLines = 1,
                textAlign = TextAlign.End,
            )
        }
        Text(
            row.total,
            Modifier.width(CountWidth),
            style = mono,
            maxLines = 1,
            textAlign = TextAlign.End,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
