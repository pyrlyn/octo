package octo.core.triggers

import kotlin.time.Instant

/** A parsed schedule. Kept as an interface so the cron library stays out of common code. */
fun interface Cron {
    /** The first moment strictly after [after] that matches, or null when none ever will. */
    suspend fun next(after: Instant): Instant?
}

fun interface CronParser {
    /** Throws [IllegalArgumentException] with a reason the user can act on. */
    fun parse(expression: String): Cron
}
