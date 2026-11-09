package octo.feature.task

import octo.core.agent.SessionStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TokenSummaryTest {
    @Test
    fun nothingUntilTheAgentCountsTokens() {
        assertNull(tokenSummary(SessionStats(contextUsed = 1_000), null))
    }

    @Test
    fun sentReceivedAndSpeedInOneLine() {
        val summary =
            tokenSummary(
                SessionStats(inputTokens = 12_300, outputTokens = 4_500, cacheReadTokens = 90_000),
                41.25,
            )!!

        assertEquals("↑ 12.3k sent  ·  ↓ 4.5k received  ·  41.3 tok/s", summary.line)
        assertEquals(90_000, summary.cacheRead)
        assertNull(summary.cacheWrite)
        assertEquals("↑ 12.3k sent  ·  ↓ 4.5k received", summary.copy(perSecond = null).line)
    }

    @Test
    fun speedIsMeasuredAcrossTheTurn() {
        var clock = 0L
        val rate = TokenRate { clock }

        assertNull(rate.update(working = false, output = 100), "no turn yet")
        clock = 1_000
        assertNull(rate.update(working = true, output = 100), "the turn starts")
        clock = 3_000
        assertEquals(50.0, rate.update(working = true, output = 200))
        clock = 5_000
        assertEquals(50.0, rate.update(working = true, output = 300))
        clock = 9_000
        assertEquals(50.0, rate.update(working = false, output = 300), "the latest turn stays shown while idle")
        clock = 10_000
        assertEquals(50.0, rate.update(working = false, output = 300))
    }

    @Test
    fun aTurnReportedOnlyAtItsEndIsStillMeasured() {
        var clock = 0L
        val rate = TokenRate { clock }

        rate.update(working = true, output = null)
        clock = 4_000
        assertEquals(25.0, rate.update(working = false, output = 100))
    }
}
