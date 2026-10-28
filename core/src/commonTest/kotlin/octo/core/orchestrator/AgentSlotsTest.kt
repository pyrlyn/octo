package octo.core.orchestrator

import octo.core.task.TaskId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentSlotsTest {
    private fun start(
        id: String,
        machine: String? = null,
    ) = Waiting(TaskId(id), machine, resume = false, forkFrom = null)

    @Test
    fun aMachineWithoutALimitNeverQueues() {
        val slots = AgentSlots(mapOf("box" to 1))
        repeat(3) { assertTrue(slots.claim(start("t$it"))) }
    }

    @Test
    fun startsOverTheLimitWaitAndGoInOrder() {
        val slots = AgentSlots(mapOf(null to 1))
        assertTrue(slots.claim(start("a")))
        assertFalse(slots.claim(start("b")))
        assertFalse(slots.claim(start("c")))
        assertTrue(slots.isWaiting(TaskId("b")))

        assertEquals(listOf(start("b")), slots.release(TaskId("a")))
        assertEquals(listOf(start("c")), slots.release(TaskId("b")))
        assertEquals(emptyList(), slots.release(TaskId("c")))
    }

    @Test
    fun aFreeSlotOnOneMachineDoesNotLetAnotherMachineThrough() {
        val slots = AgentSlots(mapOf(null to 1, "box" to 1))
        assertTrue(slots.claim(start("a")))
        assertTrue(slots.claim(start("b", "box")))
        assertFalse(slots.claim(start("c")))
        assertFalse(slots.claim(start("d", "box")))

        assertEquals(listOf(start("d", "box")), slots.release(TaskId("b")))
    }

    @Test
    fun aTaskThatHoldsASlotOrAlreadyWaitsIsNotCountedTwice() {
        val slots = AgentSlots(mapOf(null to 1))
        assertTrue(slots.claim(start("a")))
        assertTrue(slots.claim(start("a")))
        assertFalse(slots.claim(start("b")))
        assertFalse(slots.claim(start("b")))

        assertEquals(listOf(start("b")), slots.release(TaskId("a")))
        assertEquals(emptyList(), slots.release(TaskId("b")))
    }

    @Test
    fun releasingAWaitingTaskDropsItFromTheQueue() {
        val slots = AgentSlots(mapOf(null to 1))
        slots.claim(start("a"))
        slots.claim(start("b"))
        slots.claim(start("c"))

        assertEquals(emptyList(), slots.release(TaskId("b")))
        assertEquals(listOf(start("c")), slots.release(TaskId("a")))
    }

    @Test
    fun forceGivesAWaitingTaskASlotOverTheLimit() {
        val slots = AgentSlots(mapOf(null to 1))
        slots.claim(start("a"))
        slots.claim(start("b"))
        slots.claim(start("c"))

        assertEquals(start("c"), slots.force(TaskId("c")))
        assertNull(slots.force(TaskId("a")))
        // Two still run, so one release leaves the machine full.
        assertEquals(emptyList(), slots.release(TaskId("a")))
        assertEquals(listOf(start("b")), slots.release(TaskId("c")))
    }

    @Test
    fun aClosedQueueHandsOutNoSlots() {
        val slots = AgentSlots(mapOf(null to 1))
        slots.claim(start("a"))
        slots.claim(start("b"))

        assertEquals(listOf(start("b")), slots.close())
        assertEquals(emptyList(), slots.release(TaskId("a")))
    }
}
