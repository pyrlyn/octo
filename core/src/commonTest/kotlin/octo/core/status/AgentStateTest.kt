package octo.core.status

import octo.core.agent.AgentEvent
import octo.core.agent.AgentSessionRef
import octo.core.agent.Question
import octo.core.agent.StopReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AgentStateTest {
    private fun reduce(vararg events: AgentEvent): AgentState =
        events.fold(AgentState.Initial) { state, event -> state.reduce(event) }

    @Test
    fun startsAsStartingThenIdle() {
        assertEquals(AgentStatus.Starting, AgentState.Initial.status)
        assertEquals(AgentStatus.Idle, reduce(AgentEvent.SessionReady(AgentSessionRef("s"))).status)
    }

    @Test
    fun turnMakesItWorkingThenIdle() {
        assertEquals(AgentStatus.Working, reduce(AgentEvent.TurnStarted).status)
        assertEquals(AgentStatus.Idle, reduce(AgentEvent.TurnStarted, AgentEvent.TurnEnded(StopReason.EndTurn)).status)
    }

    @Test
    fun staysBlockedUntilEveryRequestIsResolved() {
        val blocked =
            reduce(
                AgentEvent.TurnStarted,
                AgentEvent.PermissionRequested("p1", "Edit file", emptyList()),
                AgentEvent.QuestionAsked("q1", listOf(Question("a", "Which?"))),
            )
        assertEquals(AgentStatus.Blocked, blocked.status)
        val oneLeft = blocked.reduce(AgentEvent.RequestResolved("p1"))
        assertEquals(AgentStatus.Blocked, oneLeft.status)
        assertEquals(AgentStatus.Working, oneLeft.reduce(AgentEvent.RequestResolved("q1")).status)
    }

    @Test
    fun errorTurnFails() {
        val state = reduce(AgentEvent.TurnStarted, AgentEvent.TurnEnded(StopReason.Error, message = "boom"))
        assertEquals(AgentStatus.Failed, state.status)
        assertEquals("boom", state.failure)
    }

    @Test
    fun cancelledTurnIsIdle() {
        assertEquals(
            AgentStatus.Idle,
            reduce(AgentEvent.TurnStarted, AgentEvent.TurnEnded(StopReason.Cancelled)).status,
        )
    }

    @Test
    fun unexpectedExitFails() {
        val state = reduce(AgentEvent.TurnStarted, AgentEvent.Exited(1))
        assertEquals(AgentStatus.Failed, state.status)
        assertNotNull(state.failure)
    }

    @Test
    fun requestedCloseStops() {
        val state = reduce(AgentEvent.TurnStarted).closing().reduce(AgentEvent.Exited(143))
        assertEquals(AgentStatus.Stopped, state.status)
    }

    @Test
    fun failureSurvivesExit() {
        val state = reduce(AgentEvent.Failed("crash"), AgentEvent.Exited(0))
        assertEquals(AgentStatus.Failed, state.status)
        assertEquals("crash", state.failure)
    }

    @Test
    fun newTurnClearsFailure() {
        val state = reduce(AgentEvent.TurnEnded(StopReason.Error), AgentEvent.TurnStarted)
        assertEquals(AgentStatus.Working, state.status)
    }
}
