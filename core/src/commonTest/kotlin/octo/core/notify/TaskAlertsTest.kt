package octo.core.notify

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import octo.core.agent.AgentId
import octo.core.agent.Question
import octo.core.status.AgentStatus
import octo.core.task.OctoState
import octo.core.task.PendingRequest
import octo.core.task.Task
import octo.core.task.TaskId
import octo.core.task.TaskView
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class TaskAlertsTest {
    private val state = MutableStateFlow(OctoState())
    private val got = mutableListOf<Alert>()

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

    private fun TestScope.set(vararg views: TaskView) {
        state.value = OctoState(views.toList())
        runCurrent()
    }

    private fun TestScope.watch(config: NotificationConfig = NotificationConfig()) {
        backgroundScope.launch { state.taskAlerts(config).toList(got) }
        runCurrent()
    }

    private fun TestScope.settle() {
        advanceTimeBy(ALERT_QUIET_MS + 1)
        runCurrent()
    }

    private val approval = PendingRequest.Permission("p", "Run ls", emptyList())

    @Test
    fun eachEventAlertsOnceTheTaskKeptQuiet() =
        runTest {
            set(view("a", AgentStatus.Working), view("b", AgentStatus.Working), view("c", AgentStatus.Working))
            watch()
            set(
                view("a", AgentStatus.Blocked, pending = listOf(approval)),
                view("b", AgentStatus.Failed, failure = "exit 1"),
                view("c", AgentStatus.Idle, unseen = true),
            )
            advanceTimeBy(ALERT_QUIET_MS - 1)
            runCurrent()
            assertEquals(emptyList(), got, "nothing before the quiet period ends")
            settle()
            assertEquals(
                listOf(
                    Alert(TaskId("a"), AlertKind.Blocked, "Task a", "Waits for your approval"),
                    Alert(TaskId("b"), AlertKind.Failed, "Task b", "Failed: exit 1"),
                    Alert(TaskId("c"), AlertKind.Done, "Task c", "Finished its turn"),
                ),
                got,
            )
        }

    @Test
    fun whatWasSoAtStartIsNotNews() =
        runTest {
            set(view("a", AgentStatus.Blocked, pending = listOf(approval)), view("b", AgentStatus.Idle, unseen = true))
            watch()
            set(
                view("a", AgentStatus.Blocked, pending = listOf(approval, approval.copy(id = "q"))),
                view("b", AgentStatus.Idle, unseen = true),
            )
            settle()
            assertEquals(emptyList(), got, "a second request on a blocked task is the same event")
        }

    @Test
    fun aBurstForOneTaskShowsItsLastEvent() =
        runTest {
            set(view("a", AgentStatus.Working))
            watch()
            set(view("a", AgentStatus.Idle, unseen = true))
            set(view("a", AgentStatus.Working))
            set(view("a", AgentStatus.Blocked, pending = listOf(PendingRequest.Questions("q", listOf(question)))))
            settle()
            assertEquals(listOf(Alert(TaskId("a"), AlertKind.Blocked, "Task a", "Has a question for you")), got)
        }

    @Test
    fun anEventGoneBeforeTheQuietPeriodEndsIsDropped() =
        runTest {
            set(view("a", AgentStatus.Working), view("b", AgentStatus.Working))
            watch()
            set(view("a", AgentStatus.Blocked, pending = listOf(approval)), view("b", AgentStatus.Idle, unseen = true))
            set(view("a", AgentStatus.Working), view("b", AgentStatus.Idle))
            settle()
            assertEquals(emptyList(), got, "answered and seen in time")
        }

    @Test
    fun aTaskAlertsAtMostOncePerCooldown() =
        runTest {
            set(view("a", AgentStatus.Working), view("b", AgentStatus.Working))
            watch()
            set(view("a", AgentStatus.Idle, unseen = true), view("b", AgentStatus.Idle, unseen = true))
            settle()
            set(view("a", AgentStatus.Working), view("b", AgentStatus.Working))
            set(view("a", AgentStatus.Blocked, pending = listOf(approval)), view("b", AgentStatus.Failed))
            settle()
            assertEquals(2, got.size, "both tasks are still cooling down")
            set(view("a", AgentStatus.Blocked, pending = listOf(approval)), view("b", AgentStatus.Working))
            advanceTimeBy(ALERT_COOLDOWN_MS)
            runCurrent()
            assertEquals(
                Alert(TaskId("a"), AlertKind.Blocked, "Task a", "Waits for your approval"),
                got.drop(2).single(),
                "the held alert shows once the cooldown ends, unless its state is gone",
            )
        }

    @Test
    fun theConfigChoosesTheEvents() =
        runTest {
            set(view("a", AgentStatus.Working), view("b", AgentStatus.Working))
            watch(NotificationConfig(done = false, blocked = false))
            set(view("a", AgentStatus.Idle, unseen = true), view("b", AgentStatus.Failed))
            settle()
            assertEquals(listOf(Alert(TaskId("b"), AlertKind.Failed, "Task b", "Failed")), got)
        }

    private companion object {
        val question = Question("q1", "Which branch?")
    }
}
