package octo.feature.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import octo.core.task.SidebarStatus
import octo.design.Dot
import octo.design.Pyrlyn
import pyrlyn.macos.Tone
import pyrlyn.macos.ink

val SidebarStatus.label: String
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

/** The macOS design's tone for [this]: busy while an agent works, a warning while it waits for you. */
val SidebarStatus.tone: Tone
    get() =
        when (this) {
            SidebarStatus.Working, SidebarStatus.Starting -> Tone.Busy
            SidebarStatus.Blocked -> Tone.Warning
            SidebarStatus.Failed -> Tone.Error
            SidebarStatus.Done -> Tone.Ok
            SidebarStatus.Queued, SidebarStatus.Idle, SidebarStatus.Stopped -> Tone.Idle
        }

@Composable
internal fun statusColor(status: SidebarStatus): Color {
    if (Pyrlyn.mac) return status.tone.ink()
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

@Composable
fun StatusDot(
    status: SidebarStatus,
    modifier: Modifier = Modifier,
) {
    Dot(statusColor(status), modifier)
}
