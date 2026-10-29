package octo.ui.vm

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import octo.core.agent.AgentId
import octo.core.io.AtomicFile
import octo.core.status.AgentStatus
import octo.core.task.TaskId
import octo.core.task.TaskView
import octo.core.task.UsageLimit
import octo.core.usage.UsageHistory
import octo.core.usage.UsageLog
import octo.core.usage.UsageRecord
import octo.core.usage.UsageReport
import octo.feature.task.TaskViewModel
import octo.feature.task.limitLabel
import octo.feature.usage.UsagePeriod
import octo.feature.usage.UsageRow
import octo.feature.usage.UsageViewModel
import octo.feature.usage.bucketEdges
import octo.feature.usage.since
import octo.testing.FakeCatalog
import octo.testing.FakeOctoCore
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UsageViewModelTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val now =
        LocalDateTime
            .of(2026, 10, 28, 15, 30)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    private val core = FakeOctoCore()

    private fun TestScope.settle() = testScheduler.runCurrent()

    private fun at(
        day: Int,
        hour: Int = 12,
    ) = LocalDateTime
        .of(2026, 10, day, hour, 0)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

    private suspend fun report(): UsageReport {
        val log = UsageLog.open(AtomicFile(Path(Files.createTempDirectory("octo-usage").toString(), "usage.json")))
        log.add(listOf(UsageRecord(at(28), AgentId("demo"), "fast", inputTokens = 1_500, outputTokens = 500)))
        val claude =
            UsageHistory {
                listOf(
                    UsageRecord(at(28, 1), AgentId("claude"), "opus", inputTokens = 10, cacheReadTokens = 2_000_000),
                    UsageRecord(at(25), AgentId("claude"), "opus", inputTokens = 90),
                    UsageRecord(at(1), AgentId("claude"), "opus", inputTokens = 900),
                )
            }
        return UsageReport(log, mapOf(AgentId("claude") to claude))
    }

    @Test
    fun periodsStartAtLocalMidnight() {
        assertEquals(at(28, 0), since(UsagePeriod.Today, now, zone))
        assertEquals(at(22, 0), since(UsagePeriod.Week, now, zone))
        assertEquals(
            LocalDateTime
                .of(2026, 9, 29, 0, 0)
                .atZone(zone)
                .toInstant()
                .toEpochMilli(),
            since(UsagePeriod.Month, now, zone),
        )
        assertNull(since(UsagePeriod.All, now, zone))
    }

    @Test
    fun totalsFollowThePeriod() =
        runTest {
            val vm = UsageViewModel(report(), FakeCatalog(core), backgroundScope, { now }, zone)
            settle()
            val week = vm.state.value
            assertFalse(week.loading)
            assertEquals(listOf("claude" to "opus", "Demo" to "fast"), week.rows.map { it.agent to it.model })
            assertEquals(
                UsageRow("claude", null, "opus", "Agent history", "100", "0", "2M", "0", "2M"),
                week.rows.first(),
            )
            assertEquals("Octo", week.rows.last().source)
            assertEquals("2M", week.total)

            vm.choose(UsagePeriod.All)
            settle()
            assertEquals(
                "1k",
                vm.state.value.rows
                    .first()
                    .input,
            )
            vm.choose(UsagePeriod.Today)
            settle()
            assertEquals(
                "10",
                vm.state.value.rows
                    .first()
                    .input,
            )
        }

    @Test
    fun aLimitShowsWhenItLifts() {
        assertEquals("Limited until 18:00", limitLabel(UsageLimit(at(28, 18)), now, zone))
        assertEquals("Limited until Oct 29, 09:00", limitLabel(UsageLimit(at(29, 9)), now, zone))
        assertEquals("Usage limit reached", limitLabel(UsageLimit(null), now, zone))
    }

    @Test
    fun resumeSendsContinueAndRestartsAStoppedAgentFirst() =
        runTest {
            val id = TaskId("t1")
            core.put(TaskView(FakeOctoCore.task(id), AgentStatus.Failed, limit = UsageLimit(at(28, 18))))
            val hidden = TaskViewModel(core, id, backgroundScope)
            val vm = TaskViewModel(core, id, backgroundScope, limits = true)
            settle()
            assertNull(hidden.state.value.limit, "the usage flag is off")
            assertEquals(UsageLimit(at(28, 18)), vm.state.value.limit)

            core.notRunning += id
            vm.resume()
            settle()
            assertEquals(listOf("restart", "prompt Continue"), core.calls)
            vm.resume()
            settle()
            assertEquals("prompt Continue", core.calls.last())
        }

    @Test
    fun theSidebarMarksALimitedTaskOnlyWithTheFlag() =
        runTest {
            core.put(TaskView(FakeOctoCore.task(TaskId("t1")), AgentStatus.Failed, limit = UsageLimit(null)))
            settle()
            val row = { vm: MainViewModel ->
                vm.state.value.groups
                    .single()
                    .tasks
                    .single()
                    .limited
            }
            assertTrue(row(MainViewModel(core, backgroundScope, limits = true)))
            assertFalse(row(MainViewModel(core, backgroundScope)))
        }

    @Test
    fun theChartHasAnHourlyOrDailyBarPerBucket() =
        runTest {
            assertEquals(24, bucketEdges(UsagePeriod.Today, now, zone).size)
            assertEquals(at(22, 0), bucketEdges(UsagePeriod.Week, now, zone).first())
            assertEquals(at(28, 0), bucketEdges(UsagePeriod.Week, now, zone).last())
            assertEquals(30, bucketEdges(UsagePeriod.Month, now, zone).size)
            assertEquals(emptyList(), bucketEdges(UsagePeriod.All, now, zone))

            val vm = UsageViewModel(report(), FakeCatalog(core), backgroundScope, { now }, zone)
            settle()
            assertEquals(listOf(0L, 0L, 0L, 90L, 0L, 0L, 2_002_010L), vm.state.value.chart)
            vm.choose(UsagePeriod.Today)
            settle()
            val today = vm.state.value.chart
            assertEquals(2_000_010L, today[1], "the 01:00 bar")
            assertEquals(2_000L, today[12], "the noon bar")
            vm.choose(UsagePeriod.All)
            settle()
            assertEquals(emptyList(), vm.state.value.chart)
        }
}
