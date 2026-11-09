package octo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import octo.feature.common.compactAge
import octo.ui.vm.TaskRow
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** The wall clock in epoch milliseconds, read again on every tick: ages are shown in whole minutes. */
@Composable
internal fun rememberNowMs(): Long {
    val now by produceState(nowMs()) {
        while (true) {
            delay(Tick)
            value = nowMs()
        }
    }
    return now
}

/** How long ago the row's agent last changed status, or null when that is not known. */
internal fun TaskRow.age(nowMs: Long): String? = activeAtMs?.let { compactAge((nowMs - it).milliseconds) }

private fun nowMs() = Clock.System.now().toEpochMilliseconds()

// Short of a minute, so a label never lags a whole minute behind.
private val Tick = 20.seconds
