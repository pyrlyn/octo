package octo.ui.vm

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import octo.core.agent.AgentId
import octo.core.agent.Question
import octo.core.attachments.Attachments
import octo.core.attachments.FileUpload
import octo.core.status.AgentStatus
import octo.core.task.PendingRequest
import octo.core.task.TaskId
import octo.core.task.TaskView
import octo.testing.FakeCatalog
import octo.testing.FakeOctoCore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AttachmentsViewModelTest {
    private val core = FakeOctoCore()
    private val id = TaskId("t1")

    private fun file(name: String) = FileUpload(name, name.encodeToByteArray())

    private fun TestScope.settle() = testScheduler.runCurrent()

    @Test
    fun filesAloneCanBeSentAndComeBackWhenTheSendFails() =
        runTest {
            core.put(TaskView(FakeOctoCore.task(id), AgentStatus.Idle))
            val vm = TaskViewModel(core, id, backgroundScope, canAttach = true)
            settle()
            assertFalse(vm.state.value.canSend)

            vm.files.add(listOf(file("a.png"), file("b.log")))
            vm.files.remove(0)
            assertTrue(vm.state.value.canSend, "a file is a message on its own")
            vm.send()
            settle()
            assertEquals("prompt  [b.log]", core.calls.last())
            assertEquals(emptyList(), vm.state.value.files)

            core.notRunning += id
            vm.files.add(listOf(file("c.log")))
            vm.send()
            settle()
            assertEquals(
                listOf("c.log"),
                vm.state.value.files
                    .map { it.name },
                "the files are kept for another try",
            )
            assertNotNull(vm.state.value.error)
        }

    @Test
    fun tooManyFilesAreRefusedAsTheyArePicked() =
        runTest {
            core.put(TaskView(FakeOctoCore.task(id), AgentStatus.Idle))
            val vm = TaskViewModel(core, id, backgroundScope, canAttach = true)
            vm.files.add(List(Attachments.MAX_FILES) { file("f$it") })

            vm.files.add(listOf(file("one-more")))

            assertEquals(Attachments.MAX_FILES, vm.state.value.files.size)
            assertNotNull(vm.state.value.error)
        }

    @Test
    fun anAnswerNamesThePathsOfItsFiles() =
        runTest {
            val questions = PendingRequest.Questions("q1", listOf(Question("why", "Why?", emptyList())))
            core.put(TaskView(FakeOctoCore.task(id), AgentStatus.Blocked, listOf(questions)))
            val vm = TaskViewModel(core, id, backgroundScope, canAttach = true)
            settle()

            vm.answer("q1", "why", "See the log")
            vm.answerFiles("q1", "why").add(listOf(file("x.log"), file("app.log")))
            vm.answerFiles("q1", "why").remove(0)
            vm.submitAnswers("q1")
            settle()

            assertEquals("attach app.log", core.calls[core.calls.size - 2])
            assertEquals(
                "respond q1 Answers(answers={why=See the log\n\nAttached files:\n- /attachments/app.log})",
                core.calls.last(),
            )
            assertEquals(emptyMap(), vm.state.value.answerFiles)
        }

    @Test
    fun aNewTaskStartsWithItsFiles() =
        runTest {
            val vm = NewTaskViewModel(core, FakeCatalog(core), backgroundScope, attachments = true)
            settle()
            assertTrue(vm.state.value.canAttach)
            vm.editRepo("/src/octo")
            testScheduler.advanceTimeBy(NewTaskUiState.BRANCH_LOOKUP_DELAY_MS)
            settle()
            vm.editTitle("Fix")
            vm.files.add(listOf(file("shot.png")))
            vm.selectAgent(AgentId("demo"))

            vm.create()
            settle()

            assertEquals("create Fix  [shot.png]", core.calls.last(), "the files go even without a prompt")
        }
}
