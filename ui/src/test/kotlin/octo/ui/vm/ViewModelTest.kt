package octo.ui.vm

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import octo.core.agent.AgentId
import octo.core.agent.PermissionKind
import octo.core.agent.PermissionMode
import octo.core.agent.PermissionOption
import octo.core.agent.Question
import octo.core.agent.Role
import octo.core.agent.VendorTerms
import octo.core.git.FileChange
import octo.core.git.FileDiff
import octo.core.git.TaskDiff
import octo.core.publish.PublishRequest
import octo.core.publish.PublishResult
import octo.core.publish.TaskPublisher
import octo.core.status.AgentStatus
import octo.core.task.PendingRequest
import octo.core.task.QueuedPrompt
import octo.core.task.SidebarStatus
import octo.core.task.TaskId
import octo.core.task.TaskView
import octo.core.task.TranscriptEntry
import octo.feature.diff.DiffViewModel
import octo.feature.newtask.NewTaskUiState
import octo.feature.newtask.NewTaskViewModel
import octo.feature.newtask.summary
import octo.feature.task.RemoveStep
import octo.feature.task.TaskViewModel
import octo.testing.FakeCatalog
import octo.testing.FakeOctoCore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelTest {
    private val core = FakeOctoCore()

    private fun view(
        id: String,
        status: AgentStatus,
        repo: String = "/src/octo",
        unseen: Boolean = false,
        pending: List<PendingRequest> = emptyList(),
    ) = TaskView(FakeOctoCore.task(TaskId(id), repo), status, pending, unseen = unseen)

    private fun TestScope.settle() = testScheduler.runCurrent()

    private fun TestScope.loadBranches() {
        testScheduler.advanceTimeBy(NewTaskUiState.BRANCH_LOOKUP_DELAY_MS)
        settle()
    }

    @Test
    fun mainGroupsByRepositoryAndPersistsTheLayout() =
        runTest {
            core.put(view("a", AgentStatus.Idle))
            core.put(view("b", AgentStatus.Blocked))
            core.put(view("c", AgentStatus.Working, repo = "/src/other/"))
            val vm = MainViewModel(core, backgroundScope)
            settle()

            val groups = vm.state.value.groups
            assertEquals(listOf("octo", "other"), groups.map { it.name })
            assertEquals(SidebarStatus.Blocked, groups[0].status)
            assertEquals(listOf(SidebarStatus.Idle, SidebarStatus.Blocked), groups[0].tasks.map { it.status })

            vm.select(TaskId("b"))
            vm.resizeSidebar(10_000f)
            vm.showDiff(true)
            settle()
            val layout = core.state.value.layout
            assertEquals(TaskId("b"), layout.selectedTask)
            assertEquals(MainUiState.MAX_SIDEBAR_WIDTH, layout.sidebarWidth)
            assertTrue(vm.state.value.diffOpen)
            assertEquals(TaskId("b"), vm.state.value.selected)
            assertTrue("seen b" in core.calls)

            core.state.update { it.copy(tasks = it.tasks.filterNot { v -> v.task.id == TaskId("b") }) }
            settle()
            assertNull(vm.state.value.selected)
        }

    @Test
    fun taskSendsPromptsAnswersRequestsAndMarksTurnsSeen() =
        runTest {
            val permission =
                PendingRequest.Permission(
                    "p1",
                    "Write",
                    listOf(PermissionOption("ok", "Allow", PermissionKind.AllowOnce)),
                )
            val questions =
                PendingRequest.Questions(
                    "q1",
                    listOf(Question("lang", "Which?", listOf("Kotlin", "Rust"), multiSelect = true)),
                )
            core.put(view("a", AgentStatus.Working, unseen = true))
            core.transcripts.value = mapOf(TaskId("a") to listOf(TranscriptEntry.Text(Role.User, "hi")))
            val vm = TaskViewModel(core, TaskId("a"), backgroundScope)
            settle()
            assertEquals(1, vm.state.value.transcript.size)
            assertTrue("seen a" in core.calls)

            vm.editDraft("  next  ")
            assertTrue(vm.state.value.canSend, "a working agent is steered or queued")
            assertTrue(vm.state.value.canCancel)
            vm.send()
            settle()
            assertEquals("", vm.state.value.draft)
            assertTrue("prompt next" in core.calls)

            core.put(view("a", AgentStatus.Blocked, pending = listOf(permission, questions)))
            settle()
            vm.choose("p1", "ok")
            vm.answer("q1", "lang", "Kotlin", toggle = true)
            vm.answer("q1", "lang", "Rust", toggle = true)
            vm.submitAnswers("q1")
            vm.cancel()
            settle()
            assertTrue("respond p1 Option(optionId=ok)" in core.calls)
            assertTrue("respond q1 Answers(answers={lang=Kotlin, Rust})" in core.calls)
            assertTrue("cancel" in core.calls)

            core.put(view("a", AgentStatus.Failed))
            settle()
            assertTrue(vm.state.value.canRestart)
            assertFalse(vm.state.value.canStop)
            vm.close()
        }

    @Test
    fun aTaskWaitingForASlotTakesPromptsAndCanStartNow() =
        runTest {
            core.put(view("a", AgentStatus.Queued))
            val vm = TaskViewModel(core, TaskId("a"), backgroundScope)
            settle()
            assertTrue(vm.state.value.canStartNow)
            assertTrue(vm.state.value.canStop)
            vm.editDraft("first")
            assertTrue(vm.state.value.canSend, "the core queues it until the agent starts")

            vm.startNow()
            settle()
            assertTrue("start now" in core.calls)

            core.put(view("a", AgentStatus.Idle))
            settle()
            assertFalse(vm.state.value.canStartNow)
            vm.close()
        }

    @Test
    fun queuedPromptsCanBeEditedAndRemoved() =
        runTest {
            val queued =
                FakeOctoCore
                    .task(
                        TaskId("a"),
                    ).copy(queue = listOf(QueuedPrompt(1, "one"), QueuedPrompt(2, "two")))
            core.put(TaskView(queued, AgentStatus.Working))
            val vm = TaskViewModel(core, TaskId("a"), backgroundScope)
            settle()
            assertEquals(
                listOf("one", "two"),
                vm.state.value.queue
                    .map { it.text },
            )

            vm.editQueued(vm.state.value.queue[0])
            vm.editQueuedText("  first  ")
            vm.saveQueued()
            settle()
            assertTrue("edit 1 first" in core.calls)
            assertNull(vm.state.value.editing)
            assertEquals(
                "first",
                vm.state.value.queue[0]
                    .text,
            )

            vm.editQueued(vm.state.value.queue[1])
            vm.removeQueued(2)
            settle()
            assertEquals(
                listOf(1),
                vm.state.value.queue
                    .map { it.id },
            )
            assertNull(vm.state.value.editing, "the removed prompt is no longer being edited")
            assertFalse(vm.state.value.canSend, "an empty draft sends nothing")
        }

    @Test
    fun removingADirtyWorktreeNeedsASecondConfirmation() =
        runTest {
            core.put(view("a", AgentStatus.Idle))
            core.dirty = true
            val vm = TaskViewModel(core, TaskId("a"), backgroundScope)
            vm.requestRemove()
            assertEquals(RemoveStep.Confirm, vm.state.value.remove)

            vm.remove(deleteWorktree = true)
            settle()
            assertEquals(RemoveStep.Dirty("/wt"), vm.state.value.remove)
            assertFalse(vm.state.value.gone)

            vm.forceRemove()
            settle()
            assertTrue("remove true true" in core.calls)
            assertTrue(vm.state.value.gone)
            assertNull(vm.state.value.remove)
        }

    @Test
    fun aFailedActionShowsAnErrorAndRestoresTheDraft() =
        runTest {
            val failing =
                object : octo.core.orchestrator.OctoCore by core {
                    override suspend fun sendPrompt(
                        taskId: TaskId,
                        text: String,
                        attachments: List<octo.core.task.NewAttachment>,
                    ): Unit = error("agent went away")
                }
            core.put(view("a", AgentStatus.Idle))
            val vm = TaskViewModel(failing, TaskId("a"), backgroundScope)
            settle()
            vm.editDraft("hello")
            vm.send()
            settle()
            assertEquals("agent went away", vm.state.value.error)
            assertEquals("hello", vm.state.value.draft)
        }

    @Test
    fun newTaskPicksAnInstalledAgentAndAsksForTerms() =
        runTest {
            core.termsPending = VendorTerms("1", "Read these", "https://example.com/terms")
            val catalog = FakeCatalog(core)
            val vm = NewTaskViewModel(core, catalog, backgroundScope)
            settle()
            assertEquals(AgentId("demo"), vm.state.value.agent)

            vm.editRepo("/src/octo")

            loadBranches()
            vm.editPrompt("Fix the build\nand more")
            assertEquals("Fix the build", vm.state.value.effectiveTitle)
            assertTrue(vm.state.value.canCreate)
            vm.selectAgent(AgentId("missing"))
            assertFalse(vm.state.value.canCreate, "an agent that is not installed cannot start")
            vm.selectAgent(AgentId("demo"))

            vm.create()
            settle()
            assertEquals(
                "Read these",
                vm.state.value.terms
                    ?.terms
                    ?.summary,
            )
            assertNull(vm.state.value.created)

            vm.acceptTerms()
            settle()
            assertEquals(listOf(AgentId("demo")), catalog.acknowledged)
            assertEquals(TaskId("t1"), vm.state.value.created)
            assertTrue("create Fix the build Fix the build\nand more" in core.calls)
        }

    @Test
    fun newTaskOffersTheAgentsPermissionModes() =
        runTest {
            val vm = NewTaskViewModel(core, FakeCatalog(core), backgroundScope)
            settle()
            assertEquals(listOf(PermissionMode.Supervised, PermissionMode.Auto), vm.state.value.permissionModes)
            assertNull(vm.state.value.chosenPermissionMode, "the config default until the user picks one")
            vm.selectPermissionMode(PermissionMode.Auto)
            vm.editRepo("/src/octo")
            loadBranches()
            vm.editPrompt("Go")
            vm.create()
            settle()
            assertTrue("create Go Go as Auto" in core.calls)

            vm.selectMachine("box")
            settle()
            assertEquals(listOf(PermissionMode.Supervised), vm.state.value.permissionModes)
            assertNull(vm.state.value.chosenPermissionMode, "a mode the agent lacks falls back to the default")
        }

    @Test
    fun aTaskChangesItsPermissionMode() =
        runTest {
            core.put(view("a", AgentStatus.Working))
            val vm = TaskViewModel(core, TaskId("a"), backgroundScope)
            vm.setPermissionMode(PermissionMode.FullAccess)
            settle()
            assertTrue("mode FullAccess" in core.calls)
            vm.close()
        }

    @Test
    fun theBaseBranchIsPickedOnlyFromTheRepositorysBranches() =
        runTest {
            core.branches = listOf("dev", "origin/main")
            val vm = NewTaskViewModel(core, FakeCatalog(core), backgroundScope)
            vm.editPrompt("Go")
            vm.editRepo("/src/oct")
            vm.editRepo("/src/octo")
            assertTrue(vm.state.value.loadingBranches)
            assertFalse(vm.state.value.canCreate, "no branches yet")
            loadBranches()
            assertEquals(listOf("branches /src/octo"), core.calls, "typing waits for a pause before asking git")
            assertEquals(listOf("dev", "origin/main"), vm.state.value.branches)
            assertEquals("dev", vm.state.value.baseRef, "without main the first branch is taken")
            assertTrue(vm.state.value.canCreate)

            vm.selectBaseRef("nope")
            assertEquals("dev", vm.state.value.baseRef, "a branch that is not listed is ignored")
            vm.selectBaseRef("origin/main")
            assertEquals("origin/main", vm.state.value.baseRef)

            vm.editRepo("")
            assertEquals(emptyList(), vm.state.value.branches)
            assertFalse(vm.state.value.canCreate)
        }

    @Test
    fun aRemoteMachineReloadsItsAgentsAndGoesIntoTheTask() =
        runTest {
            val vm = NewTaskViewModel(core, FakeCatalog(core), backgroundScope)
            settle()
            assertEquals(listOf("box"), vm.state.value.machines)
            assertNull(vm.state.value.machine)

            vm.selectMachine("box")
            assertTrue(vm.state.value.loadingAgents)
            settle()
            assertEquals(
                listOf(AgentId("claude")),
                vm.state.value.agents
                    .map { it.id },
            )
            assertEquals(AgentId("claude"), vm.state.value.agent)

            vm.editRepo("/home/me/octo")

            loadBranches()
            vm.editPrompt("Go")
            vm.create()
            settle()
            assertTrue("create Go Go on box" in core.calls, core.calls.toString())
        }

    @Test
    fun diffLoadsAndKeepsTheSelectionOnRefresh() =
        runTest {
            val a = FileDiff("a.kt", null, FileChange.Modified, binary = false, hunks = emptyList())
            val b = FileDiff("b.kt", null, FileChange.Added, binary = false, hunks = emptyList())
            core.diffResult = TaskDiff("main", "abc", listOf(a, b))
            val vm = DiffViewModel(core, TaskId("a"), backgroundScope)
            settle()
            assertFalse(vm.state.value.loading)
            assertEquals("a.kt", vm.state.value.selectedPath)

            vm.select("b.kt")
            vm.refresh()
            settle()
            assertEquals(b, vm.state.value.selected)

            core.diffResult = TaskDiff("main", "abc", listOf(a))
            vm.refresh()
            settle()
            assertEquals("a.kt", vm.state.value.selectedPath)
        }

    @Test
    fun publishSendsTheFormAndShowsTheResultOrError() =
        runTest {
            val requests = mutableListOf<PublishRequest>()
            var fail = true
            val publisher =
                object : TaskPublisher {
                    override suspend fun publish(
                        taskId: TaskId,
                        request: PublishRequest,
                    ): PublishResult {
                        requests += request
                        if (fail) error("no origin remote")
                        return PublishResult("abc", "https://example.com/pr/1", created = true)
                    }
                }
            val models = ViewModels(core, FakeCatalog(core), backgroundScope, publisher = publisher)
            assertTrue(models.canPublish)
            assertFalse(ViewModels(core, FakeCatalog(core), backgroundScope).canPublish)
            val vm = assertNotNull(models.publish(TaskId("a"), "Fix it"))
            assertEquals("Fix it", vm.state.value.commitMessage)

            vm.editBody("  Details  ")
            vm.publish()
            settle()
            assertEquals("no origin remote", vm.state.value.error)
            assertFalse(vm.state.value.running)

            fail = false
            vm.publish()
            settle()
            assertEquals(
                "https://example.com/pr/1",
                vm.state.value.result
                    ?.pullRequestUrl,
            )
            assertEquals(PublishRequest("Fix it", "Fix it", "Details"), requests.last())

            vm.editTitle(" ")
            assertFalse(vm.state.value.canPublish)
        }
}
