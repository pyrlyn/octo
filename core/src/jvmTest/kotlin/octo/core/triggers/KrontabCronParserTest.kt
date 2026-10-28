package octo.core.triggers

import kotlinx.coroutines.runBlocking
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

/** Real krontab underneath; 2026-10-28 is a Wednesday. */
class KrontabCronParserTest {
    private fun next(
        expression: String,
        after: String,
        zone: String = "UTC",
    ) = runBlocking { KrontabCronParser { ZoneId.of(zone) }.parse(expression).next(Instant.parse(after)) }

    @Test
    fun standardFieldsMatchAsInCron() {
        assertEquals(Instant.parse("2026-10-29T09:30:00Z"), next("30 9 * * *", "2026-10-28T10:00:00Z"))
        assertEquals(Instant.parse("2026-10-28T10:15:00Z"), next("*/15 * * * *", "2026-10-28T10:07:00Z"))
        assertEquals(Instant.parse("2026-10-28T10:20:00Z"), next("5/15 * * * *", "2026-10-28T10:07:00Z"))
        assertEquals(Instant.parse("2027-01-01T00:00:00Z"), next("0 0 1 1 *", "2026-10-28T10:00:00Z"))
        assertEquals(Instant.parse("2026-12-31T09:00:00Z"), next("0 9 31 * *", "2026-10-31T10:00:00Z"))
        assertEquals(Instant.parse("2026-12-31T00:00:00Z"), next("* * 31 * *", "2026-11-01T00:00:00Z"))
        assertEquals(Instant.parse("2028-02-29T00:00:00Z"), next("0 0 29 2 *", "2026-10-28T00:00:00Z"))
        assertEquals(null, next("0 0 30 2 *", "2026-10-28T00:00:00Z"))
        assertEquals(Instant.parse("2026-10-28T12:00:00Z"), next("0 8,12,18 * * *", "2026-10-28T10:00:00Z"))
    }

    @Test
    fun weekdaysCountSundayAsZeroOrSeven() {
        assertEquals(Instant.parse("2026-11-01T12:00:00Z"), next("0 12 * * 0", "2026-10-28T10:00:00Z"))
        assertEquals(Instant.parse("2026-11-01T12:00:00Z"), next("0 12 * * 7", "2026-10-28T10:00:00Z"))
        assertEquals(Instant.parse("2026-11-02T09:00:00Z"), next("0 9 * * 1-5", "2026-10-30T10:00:00Z"))
        assertEquals(Instant.parse("2026-10-31T09:00:00Z"), next("0 9 * * 6-7", "2026-10-28T10:00:00Z"))
    }

    @Test
    fun theNextRunIsStrictlyAfter() {
        assertEquals(Instant.parse("2026-10-29T09:30:00Z"), next("30 9 * * *", "2026-10-28T09:30:00Z"))
    }

    @Test
    fun localTimeFollowsTheZoneAcrossADaylightSavingChange() {
        assertEquals(Instant.parse("2026-10-24T07:00:00Z"), next("0 9 * * *", "2026-10-23T12:00:00Z", "Europe/Berlin"))
        // Summer time ends on 2026-10-25, so 09:00 there is 08:00 UTC instead of 07:00.
        assertEquals(Instant.parse("2026-10-25T08:00:00Z"), next("0 9 * * *", "2026-10-24T12:00:00Z", "Europe/Berlin"))
    }

    @Test
    fun expressionsItCannotReadAreRefusedWithAReason() {
        listOf(
            "* * *",
            "60 * * * *",
            "0 24 * * *",
            "0 0 0 * *",
            "0 0 * 13 *",
            "0 9 * * MON",
            "*/0 * * * *",
            "5-1 * * * *",
        ).forEach { assertFailsWith<IllegalArgumentException>(it) { KrontabCronParser().parse(it) } }
        val both = assertFailsWith<IllegalArgumentException> { KrontabCronParser().parse("0 9 1 * 1") }
        assertEquals(true, both.message?.contains("split it into two triggers"))
    }
}
