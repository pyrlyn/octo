package octo.feature.task

import octo.core.agent.PlanEntry
import octo.core.agent.Role
import octo.core.task.TranscriptEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TaskPlanTest {
    private val first = listOf(PlanEntry("Read the code", done = false))
    private val second = listOf(PlanEntry("Read the code", done = true), PlanEntry("Write the panel", done = false))

    @Test
    fun noPlanUntilTheAgentPostsOne() {
        assertNull(TaskUiState().plan)
        assertNull(TaskUiState(transcript = listOf(TranscriptEntry.Text(Role.User, "hi"))).plan)
        assertNull(
            TaskUiState(transcript = listOf(TranscriptEntry.Plan(emptyList()))).plan,
            "an empty plan shows nothing",
        )
    }

    @Test
    fun theLatestPlanWinsEvenWithMessagesAfterIt() {
        val transcript =
            listOf(
                TranscriptEntry.Plan(first),
                TranscriptEntry.Text(Role.Assistant, "working"),
                TranscriptEntry.Plan(second),
                TranscriptEntry.Text(Role.Assistant, "still working"),
            )

        assertEquals(second, TaskUiState(transcript = transcript).plan)
    }
}
