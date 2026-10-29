package octo.feature.common

import java.util.Locale

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
