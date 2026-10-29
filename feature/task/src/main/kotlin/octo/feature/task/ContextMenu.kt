package octo.feature.task

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import octo.design.DotSize
import octo.design.Gap
import octo.design.GhostButton
import octo.design.GlassMenu
import octo.design.LargeGap
import octo.design.Pyrlyn
import octo.design.Text
import octo.feature.common.label

private val PopupWidth = 300.dp
private val BarHeight = 8.dp
private val NumberWidth = 64.dp

/** The context button and its popup: how full the window is and what fills it. */
@Composable
internal fun ContextMenu(summary: ContextSummary) {
    var open by remember { mutableStateOf(false) }
    Box {
        GhostButton(onClick = { open = true }) { Text("Context: ${summary.label} ▾") }
        GlassMenu(open, onDismissRequest = { open = false }) {
            Column(
                Modifier.width(PopupWidth).padding(horizontal = LargeGap, vertical = Gap),
                verticalArrangement = Arrangement.spacedBy(Gap),
            ) {
                Text("Context", style = Pyrlyn.type.strong)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        summary.percent?.let { "$it% used" } ?: "Used",
                        Modifier.weight(1f),
                        style = Pyrlyn.type.strong,
                    )
                    Text(summary.usedLabel, color = Pyrlyn.colors.fgMuted)
                }
                Bar(summary)
                summary.rows.forEach { row ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Gap),
                    ) {
                        Box(Modifier.size(DotSize).background(color(row.kind), RoundedCornerShape(2.dp)))
                        Text(row.kind.label, Modifier.weight(1f))
                        Text(row.tokensLabel, Modifier.width(NumberWidth), textAlign = TextAlign.End)
                        Text(row.percentLabel, Modifier.width(NumberWidth), textAlign = TextAlign.End)
                    }
                }
            }
        }
    }
}

/** One segment per part, sized by its tokens; without a window there is nothing to measure against. */
@Composable
private fun Bar(summary: ContextSummary) {
    if (summary.window == null) return
    Row(
        Modifier
            .fillMaxWidth()
            .height(BarHeight)
            .clip(RoundedCornerShape(BarHeight / 2))
            .background(color(ContextKind.Free)),
    ) {
        val parts = summary.rows.filter { it.kind != ContextKind.Free }
        parts.forEach { row ->
            Box(Modifier.weight(row.tokens.toFloat()).height(BarHeight).background(color(row.kind)))
        }
        // An agent that reports only the total still gets its used share drawn.
        if (parts.isEmpty() && summary.used > 0) {
            Box(Modifier.weight(summary.used.toFloat()).height(BarHeight).background(Pyrlyn.colors.accentSolid))
        }
        summary.rows.firstOrNull { it.kind == ContextKind.Free }?.let {
            Box(Modifier.weight(it.tokens.toFloat()))
        }
    }
}

@Composable
private fun color(kind: ContextKind): Color =
    when (kind) {
        ContextKind.CacheRead -> Pyrlyn.colors.accentSolid
        ContextKind.CacheWrite -> Pyrlyn.colors.info
        ContextKind.Input -> Pyrlyn.colors.accentFg
        ContextKind.Output -> Pyrlyn.colors.warning
        ContextKind.Free -> Pyrlyn.colors.bgActive
    }
