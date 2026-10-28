package octo.core.notify

import octo.core.agent.AgentId
import octo.core.status.AgentStatus
import octo.core.task.PendingRequest
import octo.core.task.Task
import octo.core.task.TaskId
import octo.core.task.TaskView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NotificationRuleTest {
    private val away = Attention(windowFocused = false, openTask = null)

    private fun view(
        id: String,
        status: AgentStatus,
        unseen: Boolean = false,
        pending: List<PendingRequest> = emptyList(),
        failure: String? = null,
    ) = TaskView(
        Task(TaskId(id), "Task $id", "/src/octo", "/wt/$id", "octo/$id", "main", AgentId("demo")),
        status,
        pending,
        failure,
        unseen,
    )

    private val permission = PendingRequest.Permission("p1", "Run ls", emptyList())

    private fun rule(settings: NotificationSettings = NotificationSettings()) =
        NotificationRule(settings, quietMs = 100, cooldownMs = 1_000).also {
            it.observe(listOf(view("a", AgentStatus.Working), view("b", AgentStatus.Working)), nowMs = 0)
        }

    private fun NotificationRule.messages(
        nowMs: Long,
        attention: Attention = away,
    ) = release(nowMs, attention).map { "${it.task.value} ${it.event}: ${it.message}" }

    @Test
    fun theFirstTasksSeenOnlySetTheBaseline() {
        val rule = NotificationRule(NotificationSettings(), quietMs = 100)
        rule.observe(listOf(view("a", AgentStatus.Failed, failure = "boom")), nowMs = 0)
        assertNull(rule.nextDueMs(), "a restart does not notify for tasks that already failed")
    }

    @Test
    fun blockedFailedAndDoneNotifyOnceTheBurstIsQuiet() {
        val rule = rule()
        rule.observe(
            listOf(
                view("a", AgentStatus.Blocked, pending = listOf(permission)),
                view("b", AgentStatus.Failed, failure = "boom"),
            ),
            nowMs = 10,
        )
        assertEquals(110, rule.nextDueMs())
        assertEquals(emptyList(), rule.messages(nowMs = 50), "nothing shows while the burst may go on")
        assertEquals(listOf("a Blocked: Needs your approval", "b Failed: Failed: boom"), rule.messages(nowMs = 110))
        assertNull(rule.nextDueMs())

        rule.observe(listOf(view("a", AgentStatus.Working), view("b", AgentStatus.Working)), nowMs = 2_000)
        rule.observe(listOf(view("a", AgentStatus.Idle, unseen = true), view("b", AgentStatus.Working)), nowMs = 2_010)
        assertEquals(listOf("a Done: Finished its turn"), rule.messages(nowMs = 2_110))
    }

    @Test
    fun aBurstShowsOneNotificationWithItsLatestEvent() {
        val rule = rule()
        val question = PendingRequest.Questions("q1", emptyList())
        rule.observe(listOf(view("a", AgentStatus.Blocked, pending = listOf(permission))), nowMs = 10)
        rule.observe(listOf(view("a", AgentStatus.Working)), nowMs = 40)
        rule.observe(listOf(view("a", AgentStatus.Blocked, pending = listOf(question))), nowMs = 60)
        rule.observe(listOf(view("a", AgentStatus.Failed, failure = "exited")), nowMs = 90)
        assertEquals(emptyList(), rule.messages(nowMs = 150), "every event restarts the quiet period")
        assertEquals(listOf("a Failed: Failed: exited"), rule.messages(nowMs = 190))
    }

    @Test
    fun aBurstThatEndsWithNothingToDoIsDropped() {
        val rule = rule()
        rule.observe(listOf(view("a", AgentStatus.Blocked, pending = listOf(permission))), nowMs = 10)
        rule.observe(listOf(view("a", AgentStatus.Working)), nowMs = 20)
        assertNull(rule.nextDueMs(), "an approval answered at once needs no notification")
    }

    @Test
    fun aTaskNotifiesAtMostOncePerCooldown() {
        val rule = rule()
        rule.observe(listOf(view("a", AgentStatus.Idle, unseen = true)), nowMs = 0)
        assertEquals(listOf("a Done: Finished its turn"), rule.messages(nowMs = 100))
        rule.observe(listOf(view("a", AgentStatus.Working)), nowMs = 200)
        rule.observe(listOf(view("a", AgentStatus.Blocked, pending = listOf(permission))), nowMs = 300)
        assertEquals(1_100, rule.nextDueMs())
        assertEquals(emptyList(), rule.messages(nowMs = 400))
        assertEquals(listOf("a Blocked: Needs your approval"), rule.messages(nowMs = 1_100))
    }

    @Test
    fun settingsTurnEventsOff() {
        val rule = rule(NotificationSettings(done = false, failed = false))
        rule.observe(
            listOf(view("a", AgentStatus.Idle, unseen = true), view("b", AgentStatus.Failed)),
            nowMs = 0,
        )
        assertNull(rule.nextDueMs())
        rule.observe(
            listOf(view("a", AgentStatus.Working), view("b", AgentStatus.Blocked, pending = listOf(permission))),
            nowMs = 10,
        )
        assertEquals(listOf("b Blocked: Needs your approval"), rule.messages(nowMs = 110))
    }

    @Test
    fun theTaskTheUserIsLookingAtStaysQuiet() {
        val rule = rule()
        val blocked =
            listOf(view("a", AgentStatus.Blocked, pending = listOf(permission)), view("b", AgentStatus.Failed))
        rule.observe(blocked, nowMs = 0)
        val watchingA = Attention(windowFocused = true, openTask = TaskId("a"))
        assertEquals(listOf("b Failed: Failed"), rule.messages(nowMs = 100, watchingA), "b is not the open task")

        rule.observe(listOf(view("a", AgentStatus.Working), view("b", AgentStatus.Working)), nowMs = 2_000)
        rule.observe(blocked, nowMs = 2_000)
        assertEquals(
            listOf("a Blocked: Needs your approval", "b Failed: Failed"),
            rule.messages(nowMs = 2_100, watchingA.copy(windowFocused = false)),
            "with the window in the background even the open task notifies",
        )
    }

    @Test
    fun aRemovedTaskForgetsItsBurst() {
        val rule = rule()
        rule.observe(listOf(view("a", AgentStatus.Failed)), nowMs = 0)
        rule.observe(emptyList(), nowMs = 10)
        assertNull(rule.nextDueMs())
    }
}
