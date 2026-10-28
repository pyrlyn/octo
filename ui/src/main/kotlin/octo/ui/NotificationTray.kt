package octo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.rememberTrayState
import octo.ui.vm.NotificationsViewModel

/**
 * Posts [vm]'s alerts as OS notifications through a tray icon, the one way Compose Desktop has
 * to reach them. A click on a notification (or the icon) opens its task, and [onOpen] brings the
 * window forward.
 */
@Composable
fun ApplicationScope.NotificationTray(
    vm: NotificationsViewModel,
    onOpen: () -> Unit,
) {
    val tray = rememberTrayState()
    LaunchedEffect(vm) { vm.notifications.collect { tray.sendNotification(Notification(it.title, it.message)) } }
    Tray(
        icon = TrayIcon,
        state = tray,
        tooltip = "Octo",
        onAction = {
            vm.clicked()
            onOpen()
        },
    )
}

private object TrayIcon : Painter() {
    override val intrinsicSize = Size(ICON_SIZE, ICON_SIZE)

    override fun DrawScope.onDraw() = drawOval(Color(ICON_COLOR))
}

private const val ICON_SIZE = 256f
private const val ICON_COLOR = 0xFF7E57C2
