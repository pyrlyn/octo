package octo.adapters.claude

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import octo.adapters.base.isoMillis
import octo.core.agent.AgentEvent
import octo.core.agent.SessionSpec
import octo.core.agent.StopReason
import octo.core.usage.UsageRecord
import octo.testing.Events
import octo.testing.FakeProcessLauncher
import octo.testing.readFixture
import octo.testing.replay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClaudeHistoryTest {
    private val home = Path("src/commonTest/fixtures/home")

    private fun record(
        at: String,
        model: String,
        input: Long,
        output: Long,
        cacheRead: Long,
        cacheWrite: Long,
    ) = UsageRecord(isoMillis(at)!!, ClaudeAdapter.ID, model, input, output, cacheRead, cacheWrite)

    @Test
    fun eachReplyCountsOnceAcrossBlocksAndForkedSessions() =
        runTest {
            val history =
                ClaudeAdapter(
                    FakeProcessLauncher(backgroundScope, emptyMap()) {},
                    configDir = home,
                ).usageHistory!!
            val records = history.read().sortedBy { it.atMs }
            assertEquals(
                listOf(
                    record("2026-10-01T10:00:02.000Z", "claude-opus-5-5", 10, 5, 0, 100),
                    record("2026-10-02T09:00:00.000Z", "claude-opus-5-5", 2, 40, 110, 0),
                    record("2026-10-03T12:00:00.000Z", "claude-sonnet-5-5", 7, 9, 30, 20),
                ),
                records,
            )
            assertEquals(records, history.read().sortedBy { it.atMs }, "a second read gives the same")
        }

    @Test
    fun noConfigDirReadsNoHistoryAndAMissingOneReadsNothing() =
        runTest {
            assertNull(ClaudeAdapter(FakeProcessLauncher(backgroundScope, emptyMap()) {}).usageHistory)
            assertEquals(emptyList(), claudeHistory(Path("src/commonTest/fixtures/nowhere")).read())
        }

    @Test
    fun aRejectedTurnReportsWhenTheLimitLifts() =
        runTest {
            val launcher =
                FakeProcessLauncher(backgroundScope, mapOf("claude" to "/bin/claude")) {
                    replay(readFixture("src/commonTest/fixtures/limit.transcript"))
                }
            val adapter = ClaudeAdapter(launcher, StandardTestDispatcher(testScheduler)) { "sess-1" }
            val session = adapter.start(SessionSpec("/repo"))
            session.prompt("Go")
            val events = Events(backgroundScope, session).drain()
            assertEquals(
                listOf(AgentEvent.RateLimited(1_790_000_000_000)),
                events.filterIsInstance<AgentEvent.RateLimited>(),
                "a warning is not a limit",
            )
            assertEquals(StopReason.Error, events.filterIsInstance<AgentEvent.TurnEnded>().single().stopReason)
        }
}
