package octo.feature.task

import octo.core.agent.ContextParts
import octo.core.agent.SessionStats
import octo.feature.common.NONE
import octo.feature.common.label
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContextSummaryTest {
    @Test
    fun nothingUntilTheAgentReportsTheContext() {
        assertNull(contextSummary(SessionStats(contextWindow = 200_000)))
    }

    @Test
    fun partsAndFreeSpaceAsSharesOfTheWindow() {
        val summary =
            contextSummary(
                SessionStats(
                    contextUsed = 150_000,
                    contextWindow = 200_000,
                    contextParts = ContextParts(cacheRead = 140_000, cacheWrite = 0, input = 10_000),
                ),
            )!!

        assertEquals(75, summary.percent)
        assertEquals("75%", summary.label)
        assertEquals("150k / 200k", summary.usedLabel)
        assertEquals(
            listOf(
                ContextRow(ContextKind.CacheRead, 140_000, 70.0),
                ContextRow(ContextKind.Input, 10_000, 5.0),
                ContextRow(ContextKind.Free, 50_000, 25.0),
            ),
            summary.rows,
        )
        assertEquals("70.0%", summary.rows.first().percentLabel)
    }

    @Test
    fun withoutAWindowThereAreNoSharesOrFreeSpace() {
        val summary = contextSummary(SessionStats(contextUsed = 1_200, contextParts = ContextParts(input = 1_200)))!!

        assertNull(summary.percent)
        assertEquals("1.2k", summary.label)
        assertEquals(listOf(ContextRow(ContextKind.Input, 1_200, null)), summary.rows)
        assertEquals(NONE, summary.rows.single().percentLabel)
    }

    @Test
    fun aContextPastTheWindowLeavesNoFreeSpace() {
        val summary = contextSummary(SessionStats(contextUsed = 210, contextWindow = 200))!!

        assertEquals(105, summary.percent)
        assertEquals(emptyList(), summary.rows)
    }
}
