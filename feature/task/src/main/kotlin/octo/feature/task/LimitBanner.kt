package octo.feature.task

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import octo.core.task.UsageLimit
import octo.design.Notice
import octo.design.SecondaryButton
import octo.design.Tone
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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

/** "Usage limit reached" with when it lifts: the time today, else the date as well. */
fun limitLabel(
    limit: UsageLimit,
    nowMs: Long,
    zone: ZoneId,
): String {
    val resets = limit.resetsAtMs?.let { Instant.ofEpochMilli(it).atZone(zone) } ?: return "Usage limit reached"
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val pattern = if (resets.toLocalDate() == today) "HH:mm" else "MMM d, HH:mm"
    return "Limited until ${resets.format(DateTimeFormatter.ofPattern(pattern, Locale.ROOT))}"
}
