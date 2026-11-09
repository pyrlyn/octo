package octo.design

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import pyrlyn.macos.MacRadius
import pyrlyn.macos.MacSpace

/**
 * Shows [tip] on floating glass to the left of [content] while the pointer rests on it. Compose has
 * tooltips on the desktop only, which is why this lives in the JVM source set.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassTooltip(
    tip: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    TooltipArea(
        tooltip = {
            Box(
                Modifier
                    .widthIn(max = TooltipMaxWidth)
                    .glass(RoundedCornerShape(if (Pyrlyn.mac) MacRadius.md else TooltipRadius), GlassLevel.Floating)
                    .background(veil())
                    .padding(horizontal = if (Pyrlyn.mac) MacSpace.md else Gap, vertical = SmallGap),
            ) { tip() }
        },
        modifier = modifier,
        delayMillis = TOOLTIP_DELAY_MS,
        tooltipPlacement = TooltipPlacement.ComponentRect(Alignment.CenterStart, Alignment.CenterStart, Offset),
        content = content,
    )
}

private val Offset = DpOffset(-Gap, 0.dp)
private val TooltipMaxWidth = 320.dp
private val TooltipRadius = 8.dp
private const val TOOLTIP_DELAY_MS = 250
