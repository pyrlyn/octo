package octo.feature.task

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import octo.core.agent.PlanEntry
import octo.design.Elevation
import octo.design.Gap
import octo.design.GlassLevel
import octo.design.Pyrlyn
import octo.design.SmallGap
import octo.design.Text
import octo.design.glass
import pyrlyn.macos.Icon
import pyrlyn.macos.MacIcon

/**
 * The agent's plan beside the transcript: how many steps are done, the done ones folded under "N completed", and the
 * open ones in order. The transcript keeps every version of the plan; this shows only the latest.
 */
@Composable
internal fun PlanPanel(
    plan: List<PlanEntry>,
    modifier: Modifier = Modifier,
) {
    val c = Pyrlyn.colors
    val done = plan.filter { it.done }
    val open = plan.filterNot { it.done }
    var showDone by remember { mutableStateOf(false) }
    Column(
        modifier
            .width(PanelWidth)
            .glass(Pyrlyn.surfaceShape, GlassLevel.Floating, Elevation.E2)
            .padding(PanelPadding)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Plan", style = Pyrlyn.type.small, color = c.fgMuted, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(
                "${done.size}/${plan.size}",
                style = Pyrlyn.type.small,
                color = if (open.isEmpty()) c.success else c.accentFg,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (done.isNotEmpty()) {
            Row(
                Modifier.clickable(role = Role.Button) { showDone = !showDone },
                horizontalArrangement = Arrangement.spacedBy(SmallGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (showDone) MacIcon.ChevronDown else MacIcon.ChevronRight,
                    tint = c.fgMuted,
                    size = MarkerSize,
                )
                Text("${done.size} completed", style = Pyrlyn.type.small, color = c.fgMuted)
            }
            if (showDone) done.forEach { Step(it) }
        }
        open.forEach { Step(it) }
    }
}

@Composable
private fun Step(entry: PlanEntry) {
    val c = Pyrlyn.colors
    Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
        Box(Modifier.padding(top = MarkerTop).size(MarkerSize), contentAlignment = Alignment.Center) {
            if (entry.done) {
                Icon(MacIcon.Check, tint = c.success, size = MarkerSize)
            } else {
                Box(Modifier.size(OpenDot).border(OpenDotStroke, c.fgSubtle, CircleShape))
            }
        }
        Text(
            entry.text,
            style =
                if (entry.done) {
                    Pyrlyn.type.small.copy(
                        textDecoration = TextDecoration.LineThrough,
                    )
                } else {
                    Pyrlyn.type.small
                },
            color = if (entry.done) c.fgSubtle else c.fg,
        )
    }
}

private val PanelWidth = 260.dp
private val PanelPadding = 12.dp
private val MarkerSize = 12.dp
private val MarkerTop = 2.dp
private val OpenDot = 10.dp
private val OpenDotStroke = 1.5.dp
