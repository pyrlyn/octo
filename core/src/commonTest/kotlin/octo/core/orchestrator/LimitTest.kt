package octo.core.orchestrator

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import octo.core.agent.AgentEvent
import octo.core.agent.AgentId
import octo.core.agent.StopReason
import octo.core.task.NewTask
import octo.core.task.UsageLimit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class LimitTest {
    @Test
    fun aLimitLastsUntilTheNextTurnStarts() =
        runTest {
            val s = setup()
            val id = s.core.createTask(NewTask("/src/app", "main", AgentId("fake"), "Fix", prompt = "Go"))
            testScheduler.runCurrent()
            s.adapter.sessions
                .single()
                .emit(
                    AgentEvent.RateLimited(1_790_000_000_000),
                    AgentEvent.TurnEnded(StopReason.Error, message = "limit"),
                )
            testScheduler.runCurrent()
            assertEquals(
                UsageLimit(1_790_000_000_000),
                s.core.state.value[id]!!
                    .limit,
            )

            s.core.sendPrompt(id, "Continue")
            testScheduler.runCurrent()
            assertNull(
                s.core.state.value[id]!!
                    .limit,
            )
        }
}
