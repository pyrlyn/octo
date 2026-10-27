package octo.core.handoff

import octo.core.agent.Role
import octo.core.agent.ToolStatus
import octo.core.task.TranscriptEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HandoffTest {
    private fun user(text: String) = TranscriptEntry.Text(Role.User, text)

    private fun agent(text: String) = TranscriptEntry.Text(Role.Assistant, text)

    @Test
    fun withoutATranscriptTheRequestGoesOutAsItIs() {
        assertEquals("Do it", Handoff.build(emptyList(), "Do it", "Claude"))
        assertEquals("Do it", Handoff.build(listOf(TranscriptEntry.Notice("Started")), "Do it", "Claude"))
    }

    @Test
    fun aShortConversationCarriesOverWhole() {
        val entries =
            listOf(
                TranscriptEntry.Notice("Started"),
                user("Fix the login bug"),
                TranscriptEntry.Thought("Look at auth"),
                TranscriptEntry.Tool("t1", "Run: ls", "execute", ToolStatus.Completed, "a.kt\nb.kt"),
                agent("Fixed it"),
                agent("streaming…").copy(partial = true),
            )
        val handoff = Handoff.build(entries, "Now add a test", "Claude")
        assertTrue(handoff.startsWith("You are taking over this task from Claude."))
        assertTrue("Original request:\nFix the login bug" in handoff)
        assertTrue("Tool: Run: ls (completed)\na.kt\nb.kt\nAgent: Fixed it" in handoff)
        assertFalse("Look at auth" in handoff, "thoughts stay behind")
        assertFalse("streaming" in handoff, "a partial message is not a message yet")
        assertFalse("Started" in handoff)
        assertFalse("left out" in handoff)
        assertTrue(handoff.endsWith("The new request:\n\nNow add a test"))
        assertEquals(1, Regex("Fix the login bug").findAll(handoff).count())
    }

    @Test
    fun aLongConversationKeepsTheOriginalAndTheNewestTurnsWithinTheBudget() {
        val turns =
            (1..200).flatMap {
                listOf(
                    user("Question $it ${"q".repeat(80)}"),
                    agent("Answer $it ${"a".repeat(80)}"),
                )
            }
        val handoff = Handoff.build(listOf(user("The original goal")) + turns, "Next", "Codex", budgetTokens = 1_000)
        assertTrue(Handoff.tokens(handoff) <= 1_000)
        assertTrue("Original request:\nThe original goal" in handoff)
        assertTrue("Answer 200 " in handoff)
        assertFalse("Question 1 " in handoff)
        val kept = Regex("(Question|Answer) \\d+").findAll(handoff).count()
        assertTrue("(${turns.size - kept} earlier entries left out)" in handoff)
        assertTrue(handoff.endsWith("\n\nNext"))
    }

    @Test
    fun theNewRequestIsNeverCutAndAHugeOriginalIs() {
        val request = "r".repeat(10_000)
        val handoff =
            Handoff.build(
                listOf(user("o".repeat(10_000)), agent("Done")),
                request,
                "Claude",
                budgetTokens = 500,
            )
        assertTrue(handoff.endsWith(request))
        assertTrue("Original request:\n […]" in handoff, "no room is left for the original once the request is in")

        val roomy = Handoff.build(listOf(user("o".repeat(10_000))), "Next", "Claude", budgetTokens = 1_000)
        assertTrue(Handoff.tokens(roomy) <= 1_000)
        assertTrue("o […]" in roomy)
    }

    @Test
    fun aCommandKeepsTheEndOfItsOutput() {
        val output = "start " + "x".repeat(5_000) + " the error"
        val handoff =
            Handoff.build(
                listOf(user("Build it"), TranscriptEntry.Tool("t", "Run: make", "execute", ToolStatus.Failed, output)),
                "Fix the build",
                "Claude",
            )
        assertTrue("Tool: Run: make (failed)" in handoff)
        assertTrue("the error" in handoff)
        assertFalse("start " in handoff)
    }
}
