package octo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import octo.core.task.SidebarStatus

internal val SmallGap = 4.dp
internal val Gap = 8.dp
internal val LargeGap = 16.dp
internal val DotSize = 10.dp
internal val DividerWidth = 6.dp
internal val DialogWidth = 560.dp
internal val FileListWidth = 280.dp

private val Amber = Color(0xFFE0A100)
private val Green = Color(0xFF2E9D57)
internal val AddedLine = Color(0x332E9D57)
internal val RemovedLine = Color(0x33D93025)
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
internal fun statusColor(status: SidebarStatus): Color =
    when (status) {
        SidebarStatus.Blocked -> Amber
        SidebarStatus.Failed -> MaterialTheme.colorScheme.error
        SidebarStatus.Working -> MaterialTheme.colorScheme.primary
        SidebarStatus.Starting -> MaterialTheme.colorScheme.tertiary
        SidebarStatus.Queued -> MaterialTheme.colorScheme.secondary
        SidebarStatus.Done -> Green
        SidebarStatus.Idle -> MaterialTheme.colorScheme.outline
        SidebarStatus.Stopped -> MaterialTheme.colorScheme.outlineVariant
    }

@Composable
internal fun StatusDot(
    status: SidebarStatus,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(DotSize).background(statusColor(status), CircleShape))
}
