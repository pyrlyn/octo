package octo.core.orchestrator

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import octo.core.agent.AgentEvent
import octo.core.agent.AgentId
import octo.core.agent.PermissionKind
import octo.core.agent.PermissionOption
import octo.core.agent.StopReason
import octo.core.task.NewTask
import octo.core.task.TaskId
import octo.core.task.TaskPlace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class PlaceTest {
    private val newTask =
        NewTask(repo = "/src/app", baseRef = "main", agent = AgentId("fake"), title = "Fix", prompt = "Go")

    private fun Setup.place(id: TaskId) =
        core.state.value[id]!!
            .task.place

    private suspend fun TestScope.started(): Pair<Setup, TaskId> {
        val s = setup()
        val id = s.core.createTask(newTask)
        testScheduler.runCurrent()
        return s to id
    }

    private fun TestScope.endTurn(s: Setup) {
        s.adapter.sessions
            .single()
            .emit(AgentEvent.TurnEnded(StopReason.EndTurn))
        testScheduler.runCurrent()
    }

    @Test
    fun aPlaceIsKeptAcrossARestart() =
        runTest {
            val (s, id) = started()
            assertEquals(TaskPlace.Active, s.place(id))
            s.core.setPlace(id, TaskPlace.Pinned)
            assertEquals(TaskPlace.Pinned, s.place(id))
            s.core.close()

            val again = setup(home = s.home)
            assertEquals(TaskPlace.Pinned, again.place(id))
            assertFailsWith<UnknownTaskException> { again.core.setPlace(TaskId("nope"), TaskPlace.Settled) }
        }

    @Test
    fun aSnoozeUntilTheNextEventEndsWithTheTurn() =
        runTest {
            val (s, id) = started()
            s.core.setPlace(id, TaskPlace.Snoozed())
            endTurn(s)
            assertEquals(TaskPlace.Active, s.place(id))
        }

    @Test
    fun aTimedSnoozeLastsUntilItsTime() =
        runTest {
            val (s, id) = started()
            s.core.setPlace(id, TaskPlace.Snoozed(untilMs = testScheduler.currentTime + 60_000))
            endTurn(s)
            assertEquals(TaskPlace.Snoozed(testScheduler.currentTime + 60_000), s.place(id), "a turn end is not enough")

            testScheduler.advanceTimeBy(59_000)
            testScheduler.runCurrent()
            assertEquals(TaskPlace.Snoozed::class, s.place(id)::class)
            testScheduler.advanceTimeBy(1_000)
            testScheduler.runCurrent()
            assertEquals(TaskPlace.Active, s.place(id))
        }

    @Test
    fun aBlockedAgentWakesAnySnooze() =
        runTest {
            val (s, id) = started()
            s.core.setPlace(id, TaskPlace.Snoozed(untilMs = Long.MAX_VALUE))
            val allow = PermissionOption("ok", "Allow", PermissionKind.AllowOnce)
            s.adapter.sessions
                .single()
                .emit(AgentEvent.TurnStarted, AgentEvent.PermissionRequested("p1", "Run ls", listOf(allow)))
            testScheduler.runCurrent()
            assertEquals(TaskPlace.Active, s.place(id))
        }

    @Test
    fun aSettledTaskStaysSettled() =
        runTest {
            val (s, id) = started()
            s.core.setPlace(id, TaskPlace.Settled)
            endTurn(s)
            assertEquals(TaskPlace.Settled, s.place(id))
        }
}
