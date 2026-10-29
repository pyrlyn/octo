package octo.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import octo.core.task.UsageLimit
import octo.ui.vm.limitLabel
import java.time.ZoneId

/** Octo never resumes on its own: the user decides when spending the next window is worth it. */
@Composable
internal fun LimitBanner(
    limit: UsageLimit,
    onResume: () -> Unit,
) {
    Notice(limitLabel(limit, System.currentTimeMillis(), ZoneId.systemDefault()), Tone.Info) {
        SecondaryButton(onClick = onResume) { Text("Resume") }
    }
}
