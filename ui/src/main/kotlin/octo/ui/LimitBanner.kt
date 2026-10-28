package octo.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import octo.core.task.UsageLimit
import octo.ui.vm.limitLabel
import java.time.ZoneId

/** Octo never resumes on its own: the user decides when spending the next window is worth it. */
@Composable
internal fun LimitBanner(
    limit: UsageLimit,
    onResume: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = LargeGap, vertical = SmallGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                limitLabel(limit, System.currentTimeMillis(), ZoneId.systemDefault()),
                Modifier.weight(1f),
                color = colors.onTertiaryContainer,
            )
            TextButton(onClick = onResume) { Text("Resume") }
        }
    }
}
