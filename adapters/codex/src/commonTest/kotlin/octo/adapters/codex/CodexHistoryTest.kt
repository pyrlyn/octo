package octo.adapters.codex

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

class CodexHistoryTest {
    private fun record(
        at: String,
        model: String,
        input: Long,
        output: Long,
        cacheRead: Long,
    ) = UsageRecord(isoMillis(at)!!, CodexAdapter.ID, model, input, output, cacheRead, 0)

    @Test
    fun runningTotalsBecomeRecordsUnderEachTurnsModel() =
        runTest {
            val adapter =
                CodexAdapter(
                    FakeProcessLauncher(backgroundScope, emptyMap()) {},
                    home = Path("src/commonTest/fixtures/home"),
                )
            assertEquals(
                listOf(
                    record("2026-09-01T08:00:09.000Z", "gpt-5.5", 200, 20, 100),
                    record("2026-10-01T10:00:09.000Z", "gpt-5.5", 200, 50, 800),
                    record("2026-10-01T10:05:09.000Z", "gpt-5.5-mini", 200, 30, 300),
                ),
                adapter.usageHistory!!.read().sortedBy { it.atMs },
            )
            assertNull(CodexAdapter(FakeProcessLauncher(backgroundScope, emptyMap()) {}).usageHistory)
        }

    @Test
    fun aReachedLimitReportsWhenTheFullWindowResets() =
        runTest {
            val launcher =
                FakeProcessLauncher(backgroundScope, mapOf("codex" to "/bin/codex")) {
                    replay(readFixture("src/commonTest/fixtures/limit.transcript"))
                }
            val session = CodexAdapter(launcher, StandardTestDispatcher(testScheduler)).start(SessionSpec("/repo"))
            session.prompt("Go")
            val events = Events(backgroundScope, session).drain()
            assertEquals(
                listOf(AgentEvent.RateLimited(1_790_000_000_000)),
                events.filterIsInstance<AgentEvent.RateLimited>(),
            )
            assertEquals(StopReason.Error, events.filterIsInstance<AgentEvent.TurnEnded>().single().stopReason)
        }
}
