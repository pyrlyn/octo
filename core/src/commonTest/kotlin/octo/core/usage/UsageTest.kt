package octo.core.usage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.io.files.Path
import octo.core.agent.AgentId
import octo.core.agent.SessionStats
import octo.core.io.AtomicFile
import octo.core.status.AgentStatus
import octo.core.task.OctoState
import octo.core.task.Task
import octo.core.task.TaskId
import octo.core.task.TaskView
import octo.core.tempDir
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.time.Instant

class UsageTest {
    private val claude = AgentId("claude")
    private val kimi = AgentId("kimi")

    private fun state(
        status: AgentStatus,
        stats: SessionStats,
        agent: AgentId = kimi,
    ) = OctoState(
        listOf(
            TaskView(Task(TaskId("t1"), "Fix", "/src/app", "/wt", "octo/fix", "main", agent), status, stats = stats),
        ),
    )

    @Test
    fun aTurnIsRecordedOnceItEndsAndARestartStartsANewBaseline() {
        val recorder = UsageRecorder()
        val first = SessionStats(model = "k2", inputTokens = 100, outputTokens = 10, cacheReadTokens = 5)
        assertEquals(emptyList(), recorder.next(state(AgentStatus.Working, first), 1), "mid-turn")
        assertEquals(
            listOf(UsageRecord(2, kimi, "k2", 100, 10, 5, 0)),
            recorder.next(state(AgentStatus.Idle, first), 2),
        )
        assertEquals(emptyList(), recorder.next(state(AgentStatus.Idle, first), 3), "nothing new")
        val second = first.copy(inputTokens = 160, outputTokens = 25)
        assertEquals(
            listOf(UsageRecord(4, kimi, "k2", 60, 15, 0, 0)),
            recorder.next(state(AgentStatus.Blocked, second), 4) +
                recorder.next(state(AgentStatus.Idle, second), 4),
        )
        assertEquals(emptyList(), recorder.next(state(AgentStatus.Starting, SessionStats()), 5), "a restart")
        assertEquals(
            listOf(UsageRecord(6, kimi, "k2", 30, 0, 0, 0)),
            recorder.next(state(AgentStatus.Idle, SessionStats(model = "k2", inputTokens = 30)), 6),
        )
        assertEquals(emptyList(), recorder.next(OctoState(), 7), "a removed task")
    }

    @Test
    fun theLogKeepsWhatItWatchedAcrossARestart() =
        runTest {
            val file = Path(tempDir("octo-usage"), "usage.json")
            val log = UsageLog.open(AtomicFile(file))
            val tasks = MutableStateFlow(state(AgentStatus.Working, SessionStats(inputTokens = 40)))
            val clock =
                object : Clock {
                    override fun now() = Instant.fromEpochMilliseconds(1_000)
                }
            val watching = log.watch(tasks, backgroundScope, clock)
            testScheduler.runCurrent()
            tasks.value = state(AgentStatus.Idle, SessionStats(model = "k2", inputTokens = 40))
            // The write runs on the IO dispatcher, outside the test's virtual time.
            log.records.first { it.isNotEmpty() }
            watching.cancel()
            assertEquals(
                listOf(UsageRecord(1_000, kimi, "k2", inputTokens = 40)),
                UsageLog.open(AtomicFile(file)).records.value,
            )
        }

    @Test
    fun aBrokenLogIsLeftAloneAndRecordsNothing() =
        runTest {
            val file = AtomicFile(Path(tempDir("octo-usage"), "usage.json"))
            file.write("{ not json")
            val log = UsageLog.open(file)
            assertNotNull(log.warning)
            val watching =
                log.watch(
                    MutableStateFlow(state(AgentStatus.Idle, SessionStats(inputTokens = 1))),
                    backgroundScope,
                )
            testScheduler.runCurrent()
            watching.cancel()
            assertEquals("{ not json", file.read())
        }

    @Test
    fun anAgentWithItsOwnHistoryIsCountedFromItAlone() =
        runTest {
            val log = UsageLog.open(AtomicFile(Path(tempDir("octo-usage"), "usage.json")))
            log.add(
                listOf(
                    UsageRecord(10, claude, "opus", inputTokens = 999),
                    UsageRecord(10, kimi, "k2", inputTokens = 5, outputTokens = 1),
                    UsageRecord(20, kimi, "k2", inputTokens = 7),
                    UsageRecord(1, kimi, "k2", inputTokens = 1_000),
                ),
            )
            val history =
                UsageHistory {
                    listOf(
                        UsageRecord(15, claude, "opus", inputTokens = 50, cacheReadTokens = 500),
                        UsageRecord(16, claude, "sonnet", outputTokens = 3),
                        UsageRecord(17, kimi, "k2", inputTokens = 1_000),
                    )
                }
            val broken = UsageHistory { throw IOException("permission denied") }
            val report =
                UsageReport(
                    log,
                    mapOf(claude to history, AgentId("codex") to broken),
                    mapOf(AgentId("codex") to "Codex"),
                )
            val summary = report.totals(sinceMs = 10)
            assertEquals(
                listOf(
                    UsageTotal(claude, "opus", UsageSource.Agent, 50, 0, 500, 0),
                    UsageTotal(kimi, "k2", UsageSource.Octo, 12, 1, 0, 0),
                    UsageTotal(claude, "sonnet", UsageSource.Agent, 0, 3, 0, 0),
                ),
                summary.totals,
            )
            assertEquals(listOf("Could not read Codex's history: permission denied"), summary.warnings)
            assertEquals(
                1_012,
                report
                    .totals(sinceMs = null)
                    .totals
                    .single { it.agent == kimi }
                    .inputTokens,
            )
        }
}
