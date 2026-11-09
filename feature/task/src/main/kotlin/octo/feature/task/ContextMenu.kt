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
import octo.design.GlassMenu
import octo.design.LargeGap
import octo.design.Pyrlyn
import octo.design.Text
import octo.design.ValueButton
import octo.feature.common.NONE
import octo.feature.common.label
import octo.feature.common.tokenCount

private val PopupWidth = 300.dp
private val BarHeight = 8.dp
private val NumberWidth = 64.dp

/** The context button and its popup: how full the window is and what fills it. */
@Composable
internal fun ContextMenu(
    summary: ContextSummary,
    tokens: TokenSummary?,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        // The macOS design marks a menu by the button's outline rather than an arrow.
        val arrow = if (Pyrlyn.mac) "" else " ▾"
        ValueButton(onClick = { open = true }) { Text("Context: ${summary.label}$arrow") }
        GlassMenu(open, onDismissRequest = { open = false }) { SessionDetails(summary, tokens) }
    }
}

/** The context window and what fills it, then the tokens the session has moved and how fast. */
@Composable
internal fun SessionDetails(
    context: ContextSummary?,
    tokens: TokenSummary?,
) {
    Column(
        Modifier.width(PopupWidth).padding(horizontal = LargeGap, vertical = Gap),
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        context?.let { summary ->
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
        tokens?.let { TokenRows(it) }
    }
}

@Composable
private fun TokenRows(tokens: TokenSummary) {
    Text("Tokens this session", Modifier.padding(top = Gap), style = Pyrlyn.type.strong)
    listOfNotNull(
        "Sent" to tokens.sentLabel,
        "Received" to tokens.receivedLabel,
        tokens.cacheRead?.let { "Cache read" to tokenCount(it) },
        tokens.cacheWrite?.let { "Cache write" to tokenCount(it) },
        "Speed, latest turn" to (tokens.speedLabel ?: NONE),
    ).forEach { (name, value) ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, Modifier.weight(1f), color = Pyrlyn.colors.fgMuted)
            Text(value, textAlign = TextAlign.End)
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
