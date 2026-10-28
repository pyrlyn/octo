package octo.core.triggers

import dev.inmo.krontab.KronScheduler
import dev.inmo.krontab.buildSchedule
import korlibs.time.DateTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * Reads standard five-field cron in the computer's time zone and leaves the matching to krontab.
 * Every field is expanded here into a plain list, because krontab counts days and months from 0,
 * has no 7 for Sunday and ANDs the day of month with the weekday where cron ORs them.
 */
class KrontabCronParser(
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : CronParser {
    private val fields =
        listOf(
            Field("minute", 0..59),
            Field("hour", 0..23),
            Field("day of month", 1..31),
            Field("month", 1..12),
        )

    // 7 is Sunday as well as 0.
    private val weekday = Field("weekday", 0..7)

    override fun parse(expression: String): Cron {
        val parts = expression.trim().split(Regex("\\s+"))
        require(parts.size == fields.size + 1) { "'$expression' needs five fields: minute hour day month weekday" }
        val values = fields.mapIndexed { i, field -> field.values(parts[i]) }
        val weekdays = weekday.values(parts.last()).map { it % WEEKDAYS }.toSortedSet()
        require(values[DAY].size == fields[DAY].range.count() || weekdays.size == WEEKDAYS) {
            "'$expression' limits both the day of month and the weekday; split it into two triggers"
        }
        val template =
            (listOf("0") + fields.zip(values) { field, set -> field.krontab(set) }).joinToString(" ") +
                if (weekdays.size == WEEKDAYS) "" else " ${weekdays.joinToString(",")}w"
        return KrontabCron(template, values[DAY], zone)
    }

    private class Field(
        val name: String,
        val range: IntRange,
    ) {
        fun values(text: String): Set<Int> = text.split(',').flatMap(::item).toSortedSet()

        // krontab counts days and months from 0.
        fun krontab(values: Set<Int>) =
            if (values.size == range.count()) "*" else values.joinToString(",") { "${it - range.first}" }

        private fun item(text: String): List<Int> {
            val (span, step) = text.split('/').let { it[0] to it.getOrNull(1) }
            require(text.count { it == '/' } <= 1) { "the $name '$text' has more than one '/'" }
            val by = step?.let { number(it) } ?: 1
            require(by > 0) { "the $name step in '$text' must be above 0" }
            val (from, to) =
                when {
                    span == "*" -> range.first to range.last

                    '-' in span -> span.split('-', limit = 2).let { number(it[0]) to number(it[1]) }

                    // "5/15" runs from 5 to the end, as in cron.
                    step != null -> number(span) to range.last

                    else -> number(span).let { it to it }
                }
            require(
                from in range && to in range && from <= to,
            ) { "the $name '$text' is outside ${range.first}-${range.last}" }
            return (from..to step by).toList()
        }

        // Names such as MON or JAN are not taken: a number cannot be misread.
        private fun number(text: String) = requireNotNull(text.toIntOrNull()) { "the $name '$text' is not a number" }
    }

    private companion object {
        const val DAY = 2
        const val WEEKDAYS = 7
    }
}

private class KrontabCron(
    private val template: String,
    private val days: Set<Int>,
    private val zone: () -> ZoneId,
) : Cron {
    // One scheduler per UTC offset the zone uses, so a run is computed in the offset it falls in.
    private val schedulers = ConcurrentHashMap<Int, KronScheduler>()

    // krontab moves a day a month lacks, such as the 31st, to its last day; cron skips that month.
    override suspend fun next(after: Instant): Instant? {
        var run = match(after)
        var tries = 1
        // Null at the end for a day no month has, such as the 30th of February.
        while (run != null && local(run).dayOfMonth !in days) {
            run = if (tries++ < MONTHS_TO_TRY) match(endOfDay(run)) else null
        }
        return run
    }

    private suspend fun match(after: Instant): Instant? {
        val from = after + 1.milliseconds
        val first = at(from, offset(from)) ?: return null
        // Across a daylight-saving change the offset at the run differs from the one now.
        val offset = offset(first)
        return if (offset == offset(from)) first else at(from, offset)?.takeIf { it > after } ?: first
    }

    private suspend fun at(
        from: Instant,
        offset: Int,
    ): Instant? {
        val scheduler = schedulers.getOrPut(offset) { buildSchedule(template, offset) }
        return scheduler.next(DateTime(from.toEpochMilliseconds()))?.let {
            Instant.fromEpochMilliseconds(it.unixMillisLong)
        }
    }

    private fun local(at: Instant) =
        java.time.Instant
            .ofEpochMilli(at.toEpochMilliseconds())
            .atZone(zone())

    // Skips the rest of a clamped day at once, so "* * 31 * *" does not try every minute of it.
    private fun endOfDay(at: Instant): Instant {
        val next = local(at).toLocalDate().plusDays(1).atStartOfDay(zone())
        return Instant.fromEpochMilliseconds(next.toInstant().toEpochMilli()) - 1.milliseconds
    }

    private fun offset(at: Instant): Int =
        zone()
            .rules
            .getOffset(java.time.Instant.ofEpochMilli(at.toEpochMilliseconds()))
            .totalSeconds / SECONDS_PER_MINUTE

    private companion object {
        const val SECONDS_PER_MINUTE = 60

        /** One clamped day per month at most; enough to reach a 29th of February. */
        const val MONTHS_TO_TRY = 64
    }
}
