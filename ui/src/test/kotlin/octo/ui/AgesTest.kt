package octo.ui

import octo.core.agent.AgentId
import octo.core.task.SidebarStatus
import octo.core.task.TaskId
import octo.feature.common.compactAge
import octo.ui.vm.TaskRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class AgesTest {
    @Test
    fun anAgeIsShownInItsLargestWholeUnit() {
        val ages =
            mapOf(
                (-5).seconds to "now",
                59.seconds to "now",
                1.minutes to "1 m",
                59.minutes to "59 m",
                1.hours to "1 h",
                23.hours to "23 h",
                1.days to "1 d",
                40.days to "40 d",
            )
        assertEquals(ages.values.toList(), ages.keys.map(::compactAge))
    }

    @Test
    fun aRowWithoutAnActivityTimeHasNoAge() {
        val row = TaskRow(TaskId("t"), "Title", AgentId("claude"), "octo/t", SidebarStatus.Working)
        assertNull(row.age(nowMs = 0))
        assertEquals("3 m", row.copy(activeAtMs = 1_000).age(nowMs = 1_000 + 3.minutes.inWholeMilliseconds))
    }
}
