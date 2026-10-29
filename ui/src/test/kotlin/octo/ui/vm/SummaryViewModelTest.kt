package octo.ui.vm

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import octo.core.agent.AgentId
import octo.core.io.AtomicFile
import octo.core.task.TaskId
import octo.core.usage.UsageLog
import octo.core.usage.UsageRecord
import octo.core.usage.UsageReport
import octo.feature.agents.AgentRow
import octo.feature.agents.AgentsUiState
import octo.feature.common.label
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class SummaryViewModelTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val now =
        LocalDateTime
            .of(2026, 10, 28, 15, 30)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    private fun TestScope.settle() = testScheduler.runCurrent()

    private fun row(id: String) =
        AgentRow(TaskId(id), id, "Demo", null, "", null, "", "", "", "", null, "", stopping = false)

    @Test
    fun countsTodayAndTheRunningAgentsAndFollowsNewTurns() =
        runTest {
            val log = UsageLog.open(AtomicFile(Path(Files.createTempDirectory("octo-usage").toString(), "usage.json")))
            val demo = AgentId("demo")
            log.add(
                listOf(
                    UsageRecord(now - 86_400_000, demo, inputTokens = 9_000),
                    UsageRecord(now, demo, inputTokens = 1_200),
                ),
            )
            val agents = MutableStateFlow(AgentsUiState(listOf(row("a"), row("b"))))
            val vm = SummaryViewModel(agents, UsageReport(log, emptyMap()), backgroundScope, { now }, zone)
            settle()
            assertEquals("Today 1.2k tokens · 2 agents", vm.state.value.label)

            log.add(listOf(UsageRecord(now, demo, outputTokens = 800)))
            agents.value = AgentsUiState(listOf(row("a")))
            settle()
            assertEquals("Today 2k tokens · 1 agent", vm.state.value.label)
            vm.close()
        }

    @Test
    fun withoutTheUsageFlagOnlyAgentsShow() =
        runTest {
            val agents = MutableStateFlow(AgentsUiState())
            val vm = SummaryViewModel(agents, null, backgroundScope)
            settle()
            assertNull(vm.state.value.label, "nothing to say")
            agents.value = AgentsUiState(listOf(row("a"), row("b"), row("c")))
            settle()
            assertEquals("3 agents", vm.state.value.label)
            vm.close()
        }
}
