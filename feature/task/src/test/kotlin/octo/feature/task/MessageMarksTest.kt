package octo.feature.task

import octo.core.agent.Role
import octo.core.agent.ToolStatus
import octo.core.task.Attachment
import octo.core.task.TranscriptEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MessageMarksTest {
    @Test
    fun onlyPromptsAndRepliesGetAMarkAtTheirPlace() {
        val entries =
            listOf(
                TranscriptEntry.Text(Role.User, "Fix the build"),
                TranscriptEntry.Thought("Looking around"),
                TranscriptEntry.Tool("t1", "Run gradle", "execute", ToolStatus.Completed),
                TranscriptEntry.Text(Role.Assistant, "Fixed it."),
                TranscriptEntry.Checkpoint(1, "refs/octo/1"),
                TranscriptEntry.Notice("Agent restarted"),
                TranscriptEntry.Text(Role.User, "Thanks"),
            )

        assertEquals(
            listOf(
                MessageMark(0, user = true, preview = "Fix the build"),
                MessageMark(3, user = false, preview = "Fixed it."),
                MessageMark(6, user = true, preview = "Thanks"),
            ),
            messageMarks(entries),
        )
    }

    @Test
    fun aPreviewIsOneLine() {
        val text = TranscriptEntry.Text(Role.User, "  First line\n\n  second\tline  ")

        assertEquals("First line second line", preview(text))
    }

    @Test
    fun aLongPreviewIsCutShort() {
        val text = TranscriptEntry.Text(Role.Assistant, "word ".repeat(200))

        val shown = preview(text)

        assertTrue(shown.endsWith("…"))
        assertTrue(shown.length < text.text.length)
        assertTrue(shown.removeSuffix("…").all { it == ' ' || it in "word" })
    }

    @Test
    fun aMessageOfFilesAloneNamesThem() {
        val files = listOf(Attachment("shot.png", "/tmp/shot.png", "image/png"))
        val text = TranscriptEntry.Text(Role.User, "", attachments = files)

        assertEquals("Attached: shot.png", preview(text))
    }
}
