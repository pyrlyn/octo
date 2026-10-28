package octo.ui.vm

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import octo.core.agent.Question
import octo.core.orchestrator.OctoCore
import octo.core.status.AgentStatus
import octo.core.task.NewAttachment
import octo.core.task.PendingRequest
import octo.core.task.TaskId
import octo.core.task.TaskView
import octo.testing.FakeOctoCore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AttachmentsViewModelTest {
    private val core = FakeOctoCore()

    private fun file(name: String) = NewAttachment(name, "image/png", byteArrayOf(1, 2, 3))

    private fun view(
        status: AgentStatus,
        pending: List<PendingRequest> = emptyList(),
    ) = TaskView(FakeOctoCore.task(TaskId("a"), "/src/octo"), status, pending)

    private fun TestScope.settle() = testScheduler.runCurrent()

    @Test
    fun filesGoOutWithThePromptAndAloneAreEnough() =
        runTest {
            core.put(view(AgentStatus.Idle))
            val vm = TaskViewModel(core, TaskId("a"), backgroundScope)
            settle()
            assertFalse(vm.state.value.canSend)

            vm.attach(listOf(file("a.png"), file("b.png"), file("c.png")))
            vm.detach(1)
            assertEquals(
                listOf("a.png", "c.png"),
                vm.state.value.attachments
                    .map { it.name },
            )
            assertTrue(vm.state.value.canSend, "files alone make a message")

            vm.editDraft("look")
            vm.send()
            settle()
            assertTrue("prompt look +a.png +c.png" in core.calls)
            assertTrue(
                vm.state.value.attachments
                    .isEmpty(),
            )
            vm.close()
        }

    @Test
    fun aFailedSendKeepsTheFiles() =
        runTest {
            val failing =
                object : OctoCore by core {
                    override suspend fun sendPrompt(
                        taskId: TaskId,
                        text: String,
                        attachments: List<NewAttachment>,
                    ): Unit = error("disk full")
                }
            core.put(view(AgentStatus.Idle))
            val vm = TaskViewModel(failing, TaskId("a"), backgroundScope)
            settle()
            vm.attach(listOf(file("a.png")))
            vm.send()
            settle()
            assertEquals("disk full", vm.state.value.error)
            assertEquals(
                listOf("a.png"),
                vm.state.value.attachments
                    .map { it.name },
            )
        }

    @Test
    fun answerFilesGoWithTheirQuestion() =
        runTest {
            val questions =
                PendingRequest.Questions("q1", listOf(Question("why", "Why?", emptyList(), multiSelect = false)))
            core.put(view(AgentStatus.Blocked, listOf(questions)))
            val vm = TaskViewModel(core, TaskId("a"), backgroundScope)
            settle()

            vm.answer("q1", "why", "see the log")
            vm.attachToAnswer("q1", "why", listOf(file("log.txt"), file("shot.png")))
            vm.detachFromAnswer("q1", "why", 0)
            vm.submitAnswers("q1")
            settle()
            assertTrue("respond q1 Answers(answers={why=see the log}) +why:shot.png" in core.calls)
            assertTrue(
                vm.state.value.answerFiles
                    .isEmpty(),
            )
            vm.close()
        }
}
