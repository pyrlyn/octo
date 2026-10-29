package octo.feature.task

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import octo.core.task.TaskId
import octo.core.task.TaskPlace
import octo.design.GhostButton
import octo.design.GlassMenu
import octo.design.GlassMenuItem
import octo.design.Text
import octo.feature.common.action
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Pin, snooze or settle a task, or put it back among the active ones. */
@Composable
internal fun PlaceMenu(
    id: TaskId,
    place: TaskPlace,
    vm: TaskPlaces,
) {
    var open by remember { mutableStateOf(false) }
    val choose = { action: () -> Unit ->
        open = false
        action()
    }
    Box {
        GhostButton(onClick = { open = true }) { Text(place.label()) }
        GlassMenu(open, onDismissRequest = { open = false }) {
            if (place != TaskPlace.Active) {
                GlassMenuItem(onClick = { choose { vm.place(id, TaskPlace.Active) } }) { Text("Move to active") }
            }
            if (place != TaskPlace.Pinned) {
                GlassMenuItem(onClick = { choose { vm.place(id, TaskPlace.Pinned) } }) { Text("Pin") }
            }
            GlassMenuItem(onClick = { choose { vm.snooze(id, HOUR_MS) } }) { Text("Snooze for an hour") }
            GlassMenuItem(onClick = { choose { vm.snooze(id, DAY_MS) } }) { Text("Snooze for a day") }
            GlassMenuItem(onClick = { choose { vm.snooze(id, null) } }) { Text("Snooze until the turn ends") }
            if (place != TaskPlace.Settled) {
                GlassMenuItem(onClick = { choose { vm.place(id, TaskPlace.Settled) } }) { Text("Settle") }
            }
        }
    }
}

private fun TaskPlace.label(): String =
    when (this) {
        TaskPlace.Active -> "Active"
        TaskPlace.Pinned -> "Pinned"
        TaskPlace.Settled -> "Settled"
        is TaskPlace.Snoozed -> untilMs?.let { "Snoozed until ${TIME.format(Instant.ofEpochMilli(it))}" } ?: "Snoozed"
    }

private val TIME = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())
private const val HOUR_MS = 60 * 60_000L
private const val DAY_MS = 24 * HOUR_MS
