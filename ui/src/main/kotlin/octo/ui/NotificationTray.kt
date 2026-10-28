package octo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.isTraySupported
import androidx.compose.ui.window.rememberTrayState
import octo.core.notify.NotificationEvent
import octo.ui.vm.NotificationsViewModel
import java.awt.Desktop
import java.awt.Frame

private const val TRAY_VIEWPORT = 24f
private const val TRAY_CENTER = 12f
private const val TRAY_RADIUS = 8f

/** A filled circle; the app ships no icon of its own yet. */
private val TrayIcon: ImageVector =
    ImageVector
        .Builder("Octo", TRAY_VIEWPORT.dp, TRAY_VIEWPORT.dp, TRAY_VIEWPORT, TRAY_VIEWPORT)
        .addPath(
            PathData {
                moveTo(TRAY_CENTER - TRAY_RADIUS, TRAY_CENTER)
                arcToRelative(TRAY_RADIUS, TRAY_RADIUS, 0f, true, true, 2 * TRAY_RADIUS, 0f)
                arcToRelative(TRAY_RADIUS, TRAY_RADIUS, 0f, true, true, -2 * TRAY_RADIUS, 0f)
                close()
            },
            fill = SolidColor(Color.Black),
        ).build()

/**
 * Shows [vm]'s notifications through a tray icon, Compose Desktop's only notification API, and
 * tells it whether [window] has focus. A click on a notification or the icon brings [window] up
 * on the notified task. Call it inside the window, so the focus read is that window's.
 */
@Composable
fun ApplicationScope.NotificationTray(
    vm: NotificationsViewModel,
    window: Frame,
) {
    val focused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(focused) { vm.windowFocused(focused) }
    // Some Linux desktops have no tray; the app then runs without notifications rather than fail.
    if (!isTraySupported) return
    val tray = rememberTrayState()
    LaunchedEffect(vm) {
        vm.shown.collect { tray.sendNotification(Notification(it.title, it.message, it.event.type())) }
    }
    Tray(
        rememberVectorPainter(TrayIcon),
        state = tray,
        tooltip = "Octo",
        onAction = {
            vm.open()
            raise(window)
        },
    )
}

private fun NotificationEvent.type() =
    when (this) {
        NotificationEvent.Blocked -> Notification.Type.Warning
        NotificationEvent.Failed -> Notification.Type.Error
        NotificationEvent.Done -> Notification.Type.Info
    }

private fun raise(window: Frame) {
    if (window.extendedState and Frame.ICONIFIED !=
        0
    ) {
        window.extendedState = window.extendedState and Frame.ICONIFIED.inv()
    }
    // toFront alone leaves the app behind the active one on macOS.
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_REQUEST_FOREGROUND)) {
        Desktop.getDesktop().requestForeground(true)
    }
    window.toFront()
    window.requestFocus()
}
