package octo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import octo.core.task.TaskId
import octo.core.task.TaskPlace
import octo.ui.vm.MainViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Pin, snooze or settle a task, or put it back among the active ones. */
@Composable
internal fun PlaceMenu(
    id: TaskId,
    place: TaskPlace,
    vm: MainViewModel,
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
                DropdownMenuItem(
                    text = { Text("Move to active") },
                    onClick = { choose { vm.place(id, TaskPlace.Active) } },
                )
            }
            if (place != TaskPlace.Pinned) {
                DropdownMenuItem(text = { Text("Pin") }, onClick = { choose { vm.place(id, TaskPlace.Pinned) } })
            }
            DropdownMenuItem(text = { Text("Snooze for an hour") }, onClick = { choose { vm.snooze(id, HOUR_MS) } })
            DropdownMenuItem(text = { Text("Snooze for a day") }, onClick = { choose { vm.snooze(id, DAY_MS) } })
            DropdownMenuItem(
                text = { Text("Snooze until the turn ends") },
                onClick = { choose { vm.snooze(id, null) } },
            )
            if (place != TaskPlace.Settled) {
                DropdownMenuItem(text = { Text("Settle") }, onClick = { choose { vm.place(id, TaskPlace.Settled) } })
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
