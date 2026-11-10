package octo.feature.task

import octo.core.agent.Role
import octo.core.agent.ToolStatus
import octo.core.task.Attachment
import octo.core.task.TranscriptEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistoryRailTest {
    private val transcript =
        listOf(
            TranscriptEntry.Checkpoint(0, "refs/octo/t/0"),
            TranscriptEntry.Text(Role.User, "Add OAuth login", atMs = 1_000),
            TranscriptEntry.Thought("Looking at the auth module."),
            TranscriptEntry.Tool("t1", "./gradlew test", "execute", ToolStatus.Completed),
            TranscriptEntry.Text(Role.Assistant, "The login route is in place."),
            TranscriptEntry.Text(Role.Assistant, "Tests pass too."),
            TranscriptEntry.Text(Role.User, "Keep the session in a cookie"),
            TranscriptEntry.Notice("Stopped"),
            TranscriptEntry.Text(Role.User, "Go on"),
            TranscriptEntry.Text(Role.Assistant, "Done."),
        )

    @Test
    fun aTickPerPromptWithTheFirstReplyOfItsTurn() {
        assertEquals(
            listOf(
                HistoryTurn(1, "Add OAuth login", "The login route is in place.", 1_000),
                HistoryTurn(6, "Keep the session in a cookie", null, null),
                HistoryTurn(8, "Go on", "Done.", null),
            ),
            historyTurns(transcript),
        )
    }

    @Test
    fun theTurnInViewIsTheOneTheTopOfTheListIsIn() {
        val turns = historyTurns(transcript)
        // Above the first prompt, at it, inside its reply, at the next prompt, and at the end.
        assertEquals(listOf(0, 0, 0, 1, 2), listOf(0, 1, 4, 6, 9).map { currentTurn(turns, it) })
    }

    @Test
    fun theNeighboursOfTheCurrentTickAreNear() {
        assertEquals(
            listOf(HistoryTick.Default, HistoryTick.Near, HistoryTick.Current, HistoryTick.Near, HistoryTick.Default),
            (0..4).map { tick(it, current = 2) },
        )
    }

    @Test
    fun theRailHidesWithFewerThanTwoPrompts() {
        assertFalse(showsRail(historyTurns(emptyList())))
        assertFalse(showsRail(historyTurns(transcript.take(6))))
        assertTrue(showsRail(historyTurns(transcript.take(7))))
    }

    @Test
    fun theHeadingSaysHowLongAgo() {
        val hour = 3_600_000L
        assertEquals("You · now", turnHeading(atMs = 0, nowMs = 30_000))
        assertEquals("You · 1h ago", turnHeading(atMs = 0, nowMs = hour + 1))
        assertEquals("You", turnHeading(atMs = null, nowMs = hour))
    }

    @Test
    fun aPreviewIsOneLineCutShort() {
        assertEquals("Fix the build and run the tests", preview(user("  Fix the build\n\n and run\tthe tests ")))
        val long = preview(user("word ".repeat(100)))
        assertTrue(long.length <= 281 && long.endsWith("…"))
    }

    @Test
    fun aMessageOfFilesAloneNamesThem() {
        val files = listOf(Attachment("shot.png", "/tmp/shot.png", "image/png"))
        assertEquals("Attached: shot.png", preview(TranscriptEntry.Text(Role.User, "", attachments = files)))
    }

    private fun user(text: String) = TranscriptEntry.Text(Role.User, text)
}
