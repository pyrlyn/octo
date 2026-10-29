package octo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import octo.core.task.SidebarStatus
import octo.ui.theme.Pyrlyn

internal val SmallGap = 4.dp
internal val Gap = 8.dp
internal val LargeGap = 16.dp
internal val DotSize = 8.dp
internal val PanePadding = 20.dp
internal val DividerWidth = 8.dp
internal val DialogWidth = 560.dp
internal val FileListWidth = 280.dp

internal const val PARTIAL_ALPHA = 0.6f

internal val SidebarStatus.label: String
    get() =
        when (this) {
            SidebarStatus.Blocked -> "Needs you"
            SidebarStatus.Failed -> "Failed"
            SidebarStatus.Working -> "Working"
            SidebarStatus.Starting -> "Starting"
            SidebarStatus.Queued -> "Queued"
            SidebarStatus.Done -> "Done"
            SidebarStatus.Idle -> "Idle"
            SidebarStatus.Stopped -> "Stopped"
        }

@Composable
internal fun statusColor(status: SidebarStatus): Color {
    val c = Pyrlyn.colors
    return when (status) {
        SidebarStatus.Blocked -> c.warning
        SidebarStatus.Failed -> c.danger
        SidebarStatus.Working -> c.accentFg
        SidebarStatus.Starting -> c.info
        SidebarStatus.Queued -> c.fgMuted
        SidebarStatus.Done -> c.success
        SidebarStatus.Idle -> c.fgSubtle
        SidebarStatus.Stopped -> c.fgDisabled
    }
}

/** Background of an added diff line. */
@Composable
internal fun addedLine(): Color = Pyrlyn.colors.success.copy(alpha = DIFF_TINT)

/** Background of a removed diff line. */
@Composable
internal fun removedLine(): Color = Pyrlyn.colors.danger.copy(alpha = DIFF_TINT)

private const val DIFF_TINT = 0.18f

@Composable
internal fun StatusDot(
    status: SidebarStatus,
    modifier: Modifier = Modifier,
) {
    Dot(statusColor(status), modifier)
}

/** A status is always a dot and a word, so the colour is never the only signal. */
@Composable
internal fun Dot(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(DotSize).background(color, CircleShape))
}
