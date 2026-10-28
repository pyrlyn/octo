package octo.core.orchestrator

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import octo.core.agent.AgentEvent
import octo.core.agent.AgentId
import octo.core.agent.Capability
import octo.core.agent.SessionStats
import octo.core.agent.StopReason
import octo.core.task.NewTask
import octo.core.task.TaskId
import octo.core.task.TaskPlace
import octo.core.task.TranscriptEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class IdleCompactionTest {
    private val newTask =
        NewTask(repo = "/src/app", baseRef = "main", agent = AgentId("fake"), title = "Fix", prompt = "Go")

    private suspend fun TestScope.started(
        compactIdle: Long? = 200_000,
        capabilities: Set<Capability> = setOf(Capability.Compact),
    ): Pair<Setup, TaskId> {
        val s = setup(adapter = FakeAdapter(capabilities), compactIdle = compactIdle)
        val id = s.core.createTask(newTask)
        testScheduler.runCurrent()
        return s to id
    }

    private val Setup.session get() = adapter.sessions.single()

    /** The last request started now and cached [context] tokens for five minutes. */
    private fun TestScope.endTurn(
        s: Setup,
        context: Long = 250_000,
    ) {
        s.session.emit(
            AgentEvent.StatsUpdated(SessionStats(promptCacheTtlMs = 300_000)),
            AgentEvent.StatsUpdated(SessionStats(contextUsed = context)),
            AgentEvent.TurnEnded(StopReason.EndTurn),
        )
        testScheduler.runCurrent()
    }

    private fun TestScope.after(ms: Long) {
        testScheduler.advanceTimeBy(ms)
        testScheduler.runCurrent()
    }

    @Test
    fun aLargeIdleConversationIsCompactedTwoMinutesBeforeItsCacheExpires() =
        runTest {
            val (s, id) = started()
            endTurn(s)
            after(179_000)
            assertEquals(0, s.session.compactions)
            after(1_000)
            assertEquals(1, s.session.compactions)
            val notices =
                s.core
                    .transcript(id)
                    .first()
                    .filterIsInstance<TranscriptEntry.Notice>()
            assertTrue(notices.any { "Compacting" in it.text })
        }

    @Test
    fun aPromptCancelsTheCompaction() =
        runTest {
            val (s, id) = started()
            endTurn(s)
            after(60_000)
            s.core.sendPrompt(id, "One more thing")
            s.session.emit(AgentEvent.TurnEnded(StopReason.EndTurn))
            after(600_000)
            assertEquals(0, s.session.compactions)
        }

    @Test
    fun aTurnIsCompactedAtMostOnce() =
        runTest {
            val (s, _) = started()
            endTurn(s)
            after(180_000)
            // The compaction's own request reports a fresh cache.
            endTurn(s)
            after(600_000)
            assertEquals(1, s.session.compactions)
        }

    @Test
    fun aSmallContextIsLeftAlone() =
        runTest {
            val (s, _) = started()
            endTurn(s, context = 100_000)
            after(600_000)
            assertEquals(0, s.session.compactions)
        }

    @Test
    fun aLowerThresholdCompactsASmallerContext() =
        runTest {
            val (s, _) = started(compactIdle = 50_000)
            endTurn(s, context = 100_000)
            after(180_000)
            assertEquals(1, s.session.compactions)
        }

    @Test
    fun aSnoozedTaskIsLeftAlone() =
        runTest {
            val (s, id) = started()
            endTurn(s)
            s.core.setPlace(id, TaskPlace.Snoozed(untilMs = testScheduler.currentTime + 3_600_000))
            after(600_000)
            assertEquals(0, s.session.compactions)
        }

    @Test
    fun nothingHappensWithTheFlagOffOrAnAgentThatCannotCompact() =
        runTest {
            val (off, _) = started(compactIdle = null)
            endTurn(off)
            val (unable, _) = started(capabilities = emptySet())
            endTurn(unable)
            after(600_000)
            assertEquals(0, off.session.compactions + unable.session.compactions)
        }
}
