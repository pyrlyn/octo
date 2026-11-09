package octo.feature.common

import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** Shown where a figure is not known. */
const val NONE = "—"

private const val THOUSAND = 1_000.0

/** 950, 1k, 12.3k, 1.2M. */
fun tokenCount(count: Long): String =
    when {
        count < THOUSAND -> count.toString()
        count < THOUSAND * THOUSAND -> compact(count / THOUSAND, "k")
        else -> compact(count / THOUSAND / THOUSAND, "M")
    }

private fun compact(
    value: Double,
    suffix: String,
): String = String.format(Locale.ROOT, "%.1f", value).removeSuffix(".0") + suffix

/** How long ago, for a list row: now, 3 m, 2 h, 1 d. A time ahead of the clock reads as now. */
fun compactAge(age: Duration): String =
    when {
        age < 1.minutes -> "now"
        age < 1.hours -> "${age.inWholeMinutes} m"
        age < 1.days -> "${age.inWholeHours} h"
        else -> "${age.inWholeDays} d"
    }
