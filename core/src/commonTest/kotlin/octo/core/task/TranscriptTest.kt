package octo.core.task

import octo.core.agent.AgentEvent
import octo.core.agent.AgentId
import octo.core.agent.PlanEntry
import octo.core.agent.Role
import octo.core.agent.StopReason
import octo.core.agent.ToolStatus
import octo.core.status.AgentStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TranscriptTest {
    private fun fold(vararg events: AgentEvent) = events.fold(emptyList<TranscriptEntry>(), Transcript::reduce)

    @Test
    fun deltasStreamIntoOneMessage() {
        val streaming =
            fold(AgentEvent.MessageDelta(Role.Assistant, "Hel"), AgentEvent.MessageDelta(Role.Assistant, "lo"))
        assertEquals(listOf(TranscriptEntry.Text(Role.Assistant, "Hello", partial = true)), streaming)
        assertEquals(emptyList(), Transcript.settled(streaming))

        val done = Transcript.reduce(streaming, AgentEvent.Message(Role.Assistant, "Hello."))
        assertEquals(listOf(TranscriptEntry.Text(Role.Assistant, "Hello.")), done)
    }

    @Test
    fun toolsPlansAndRequestsUpdateInPlace() {
        val entries =
            fold(
                AgentEvent.ToolCall("t1", "Read", "read", ToolStatus.Pending),
                AgentEvent.PlanUpdated(listOf(PlanEntry("a", done = false))),
                AgentEvent.PlanUpdated(listOf(PlanEntry("a", done = true))),
                AgentEvent.ToolCall("t1", "Read file", "read", ToolStatus.Completed),
                AgentEvent.PermissionRequested("p1", "Run ls", emptyList()),
                AgentEvent.RequestResolved("p1"),
                AgentEvent.TurnEnded(StopReason.EndTurn),
                AgentEvent.TurnEnded(StopReason.Error, message = "boom"),
            )
        assertEquals(
            listOf(
                TranscriptEntry.Tool("t1", "Read file", "read", ToolStatus.Completed),
                TranscriptEntry.Plan(listOf(PlanEntry("a", done = true))),
                TranscriptEntry.Request("p1", "Run ls", resolved = true),
                TranscriptEntry.Notice("boom", error = true),
            ),
            entries,
        )
    }

    @Test
    fun transcriptIsBounded() {
        val events = Array(Transcript.MAX_ENTRIES + 10) { AgentEvent.Thought("t$it") }
        val entries = fold(*events)
        assertEquals(Transcript.MAX_ENTRIES, entries.size)
        assertEquals(TranscriptEntry.Thought("t10"), entries.first())
    }

    @Test
    fun rollupFollowsThePriorityLadder() {
        fun view(
            status: AgentStatus,
            unseen: Boolean = false,
        ) = TaskView(Task(TaskId("x"), "x", "/r", "/w", "b", "main", AgentId("a")), status, unseen = unseen)

        assertNull(emptyList<TaskView>().rollup())
        assertEquals(
            SidebarStatus.Done,
            listOf(view(AgentStatus.Idle, unseen = true), view(AgentStatus.Stopped)).rollup(),
        )
        assertEquals(SidebarStatus.Failed, listOf(view(AgentStatus.Working), view(AgentStatus.Failed)).rollup())
        assertEquals(
            SidebarStatus.Blocked,
            listOf(view(AgentStatus.Failed), view(AgentStatus.Blocked), view(AgentStatus.Idle)).rollup(),
        )
        assertEquals(SidebarStatus.Idle, listOf(view(AgentStatus.Idle), view(AgentStatus.Stopped)).rollup())
    }
}
