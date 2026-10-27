package octo.core.orchestrator

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import octo.core.agent.AdapterRegistry
import octo.core.agent.AgentEvent
import octo.core.agent.AgentId
import octo.core.agent.AgentSessionRef
import octo.core.agent.Capability
import octo.core.agent.PermissionKind
import octo.core.agent.PermissionOption
import octo.core.agent.RequestResponse
import octo.core.agent.Role
import octo.core.agent.StopReason
import octo.core.agent.VendorTerms
import octo.core.flags.FeatureFlags
import octo.core.io.AtomicFile
import octo.core.process.ProcessLauncher
import octo.core.process.ProcessSpec
import octo.core.process.RunningProcess
import octo.core.status.AgentStatus
import octo.core.task.NewTask
import octo.core.task.PendingRequest
import octo.core.task.QueuedPrompt
import octo.core.task.SidebarStatus
import octo.core.task.TaskId
import octo.core.task.TranscriptEntry
import octo.core.tempDir
import octo.core.terms.TermsGate
import octo.core.terms.TermsNotAcknowledgedException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OrchestratorTest {
    private val newTask =
        NewTask(
            repo = "/src/app/sub",
            baseRef = "main",
            agent = AgentId("fake"),
            title = "Fix the Login bug!",
            prompt = "Go",
        )

    @Test
    fun aTaskOnARemoteMachineUsesThatMachinesAgentsAndWorkspace() =
        runTest {
            val remoteAdapter = FakeAdapter()
            val remoteWorkspace = FakeWorkspace()
            val noProcesses =
                object : ProcessLauncher {
                    override suspend fun which(command: String): String? = null

                    override suspend fun launch(spec: ProcessSpec): RunningProcess = error("not used")
                }
            val box =
                RemoteMachine(
                    AdapterRegistry(listOf(remoteAdapter), FeatureFlags()),
                    remoteWorkspace,
                    noProcesses,
                    "/srv/octo",
                )
            val s = setup(machines = mapOf("box" to box))

            val id = s.core.createTask(newTask.copy(machine = "box"))
            testScheduler.runCurrent()
            val task =
                s.core.state.value[id]!!
                    .task
            assertEquals("box", task.machine)
            assertEquals("/srv/octo/app/fix-the-login-bug-id1", task.worktree)
            assertEquals(task.branch, remoteWorkspace.worktrees[task.worktree])
            assertTrue(s.workspace.worktrees.isEmpty())
            assertEquals(listOf(task.worktree), remoteAdapter.specs.map { it.workingDir })
            assertTrue(s.adapter.specs.isEmpty())

            s.core.removeTask(id, deleteWorktree = true, force = false)
            assertEquals(listOf(task.worktree), remoteWorkspace.removed)
            assertFailsWith<UnknownMachineException> { s.core.createTask(newTask.copy(machine = "gone")) }
        }

    @Test
    fun createsATaskAndTracksItsAgent() =
        runTest {
            val s = setup()
            val id = s.core.createTask(newTask)
            testScheduler.runCurrent()

            val task =
                s.core.state.value[id]!!
                    .task
            assertEquals("octo/fix-the-login-bug-id1", task.branch)
            assertEquals(Path(s.home, "worktrees", "app", "fix-the-login-bug-id1").toString(), task.worktree)
            assertEquals(task.branch, s.workspace.worktrees[task.worktree])
            assertEquals(
                task.worktree,
                s.adapter.specs
                    .single()
                    .workingDir,
            )
            assertEquals(AgentSessionRef("s1"), task.sessionRef)
            val session = s.adapter.sessions.single()
            assertEquals(listOf("Go"), session.prompts)
            assertEquals(
                AgentStatus.Working,
                s.core.state.value[id]!!
                    .status,
                "a sent prompt reads as working before the agent says so",
            )

            val allow = PermissionOption("ok", "Allow", PermissionKind.AllowOnce)
            session.emit(AgentEvent.TurnStarted, AgentEvent.PermissionRequested("p1", "Run ls", listOf(allow)))
            testScheduler.runCurrent()
            val blocked = s.core.state.value[id]!!
            assertEquals(AgentStatus.Blocked, blocked.status)
            assertEquals(listOf(PendingRequest.Permission("p1", "Run ls", listOf(allow))), blocked.pending)

            s.core.respond(id, "p1", RequestResponse.Option("ok"))
            assertEquals(listOf<Pair<String, RequestResponse>>("p1" to RequestResponse.Option("ok")), session.responses)
            session.emit(
                AgentEvent.RequestResolved("p1"),
                AgentEvent.Message(Role.Assistant, "Done."),
                AgentEvent.TurnEnded(StopReason.EndTurn),
            )
            testScheduler.runCurrent()
            val done = s.core.state.value[id]!!
            assertEquals(AgentStatus.Idle, done.status)
            assertEquals(emptyList(), done.pending)
            assertEquals(SidebarStatus.Done, done.sidebarStatus)
            s.core.markSeen(id)
            assertEquals(
                SidebarStatus.Idle,
                s.core.state.value[id]!!
                    .sidebarStatus,
            )

            assertEquals(
                listOf(
                    TranscriptEntry.Text(Role.User, "Go"),
                    TranscriptEntry.Request("p1", "Run ls", resolved = true),
                    TranscriptEntry.Text(Role.Assistant, "Done."),
                ),
                s.core.transcript(id).first(),
            )
            s.core.cancel(id)
            assertEquals(1, session.cancelled)
        }

    @Test
    fun stopRestartAndResume() =
        runTest {
            val s = setup()
            val id = s.core.createTask(newTask.copy(prompt = null))
            testScheduler.runCurrent()
            s.core.stop(id)
            assertEquals(
                AgentStatus.Stopped,
                s.core.state.value[id]!!
                    .status,
            )
            assertFailsWith<TaskNotRunningException> { s.core.sendPrompt(id, "hi") }

            s.core.restart(id)
            testScheduler.runCurrent()
            assertEquals(
                AgentSessionRef("s1"),
                s.adapter.specs
                    .last()
                    .resume,
            )
            assertEquals(
                AgentStatus.Idle,
                s.core.state.value[id]!!
                    .status,
            )
        }

    @Test
    fun restartWithoutResumeStartsFreshAndSaysSo() =
        runTest {
            val s = setup(adapter = FakeAdapter(capabilities = emptySet()))
            val id = s.core.createTask(newTask.copy(prompt = null))
            testScheduler.runCurrent()
            s.core.restart(id)
            testScheduler.runCurrent()
            assertNull(
                s.adapter.specs
                    .last()
                    .resume,
            )
            assertEquals(
                AgentSessionRef("s2"),
                s.core.state.value[id]!!
                    .task.sessionRef,
            )
            assertTrue(
                s.core
                    .transcript(id)
                    .first()
                    .any { it is TranscriptEntry.Notice && "fresh" in it.text },
            )
        }

    @Test
    fun aCrashedAgentFailsOnlyItsTask() =
        runTest {
            val s = setup()
            val id = s.core.createTask(newTask.copy(prompt = null))
            testScheduler.runCurrent()
            s.adapter.sessions
                .single()
                .emit(AgentEvent.Exited(137))
            testScheduler.runCurrent()
            val view = s.core.state.value[id]!!
            assertEquals(AgentStatus.Failed, view.status)
            assertEquals("Agent exited with code 137", view.failure)
        }

    @Test
    fun failedStartShowsOnTheTask() =
        runTest {
            val s = setup(adapter = FakeAdapter(failStart = true))
            val id = s.core.createTask(newTask)
            testScheduler.runCurrent()
            val view = s.core.state.value[id]!!
            assertEquals(AgentStatus.Failed, view.status)
            assertEquals("fake is not installed", view.failure)
            assertTrue(
                s.core
                    .transcript(id)
                    .first()
                    .any { it is TranscriptEntry.Notice && "first prompt" in it.text },
            )
        }

    @Test
    fun pendingTermsBlockTheTaskBeforeTheRepositoryIsTouched() =
        runTest {
            val adapter = FakeAdapter(terms = VendorTerms("1", "Read this.", "https://example.com"))
            val s = setup(adapter = adapter)
            assertFailsWith<TermsNotAcknowledgedException> { s.core.createTask(newTask) }
            assertEquals(emptyMap(), s.workspace.worktrees)

            TermsGate(AtomicFile(Path(s.home, "acknowledgements.json"))).acknowledge(adapter)
            s.core.createTask(newTask)
            assertEquals(1, s.workspace.worktrees.size)
        }

    @Test
    fun removeKeepsADirtyWorktreeUnlessForced() =
        runTest {
            val s = setup()
            val id = s.core.createTask(newTask.copy(prompt = null))
            testScheduler.runCurrent()
            val worktree =
                s.core.state.value[id]!!
                    .task.worktree
            s.workspace.dirty += worktree

            assertFailsWith<WorktreeDirtyException> { s.core.removeTask(id, deleteWorktree = true) }
            assertEquals(
                AgentStatus.Idle,
                s.core.state.value[id]!!
                    .status,
            )
            s.core.removeTask(id, deleteWorktree = true, force = true)
            assertEquals(listOf(worktree), s.workspace.removed)
            assertNull(s.core.state.value[id])
        }

    @Test
    fun stateSurvivesARestartWithEveryTaskStopped() =
        runTest {
            val home = tempDir("octo-core")
            val first = setup(home = home)
            val id = first.core.createTask(newTask)
            testScheduler.runCurrent()
            first.adapter.sessions
                .single()
                .emit(AgentEvent.MessageDelta(Role.Assistant, "partial"))
            testScheduler.runCurrent()
            first.core.updateLayout(
                first.core.state.value.layout
                    .copy(selectedTask = id),
            )
            first.core.close()

            val second = setup(home = home)
            val view = second.core.state.value[id]!!
            assertEquals(AgentStatus.Stopped, view.status)
            assertEquals(AgentSessionRef("s1"), view.task.sessionRef)
            assertEquals(id, second.core.state.value.layout.selectedTask)
            assertEquals(TaskId("id1"), id)
            val transcript = second.core.transcript(id).first()
            assertEquals(TranscriptEntry.Text(Role.User, "Go"), transcript.first())
            assertTrue(transcript.none { it is TranscriptEntry.Text && it.partial })

            second.core.restart(id)
            testScheduler.runCurrent()
            assertEquals(
                AgentSessionRef("s1"),
                second.adapter.specs
                    .single()
                    .resume,
            )
        }

    @Test
    fun aBusyAgentQueuesUntilIdleAndTheQueueCanBeEdited() =
        runTest {
            val s = setup()
            val id = s.core.createTask(newTask)
            testScheduler.runCurrent()
            val session = s.adapter.sessions.single()

            s.core.sendPrompt(id, "Next")
            s.core.sendPrompt(id, "Third")
            s.core.editQueued(id, 1, "Next, please")
            s.core.removeQueued(id, 2)
            assertEquals(listOf("Go"), session.prompts)
            assertEquals(listOf(QueuedPrompt(1, "Next, please")), queue(s, id))
            assertFailsWith<IllegalStateException> { s.core.editQueued(id, 2, "gone") }

            session.emit(AgentEvent.TurnStarted, AgentEvent.TurnEnded(StopReason.EndTurn))
            testScheduler.runCurrent()
            assertEquals(listOf("Go", "Next, please"), session.prompts)
            assertEquals(emptyList(), queue(s, id))
            assertEquals(
                AgentStatus.Working,
                s.core.state.value[id]!!
                    .status,
            )
            assertEquals(
                listOf("Go", "Next, please"),
                s.core
                    .transcript(id)
                    .first()
                    .filterIsInstance<TranscriptEntry.Text>()
                    .map { it.text },
            )
        }

    @Test
    fun aSteeringAgentTakesTheTextIntoTheTurnAndARefusalQueuesIt() =
        runTest {
            val s = setup(adapter = FakeAdapter(capabilities = setOf(Capability.Resume, Capability.Steer)))
            val id = s.core.createTask(newTask)
            testScheduler.runCurrent()
            val session = s.adapter.sessions.single()

            s.core.sendPrompt(id, "Use tabs")
            assertEquals(listOf("Use tabs"), session.steered)
            assertEquals(emptyList(), queue(s, id))
            assertEquals(
                TranscriptEntry.Text(Role.User, "Use tabs"),
                s.core
                    .transcript(id)
                    .first()
                    .last(),
            )

            session.refuseSteer = true
            s.core.sendPrompt(id, "And spaces")
            assertEquals(listOf(QueuedPrompt(1, "And spaces")), queue(s, id))
        }

    @Test
    fun theQueueSurvivesARestartAndIsSentOnceTheAgentIsUp() =
        runTest {
            val home = tempDir("octo-core")
            val first = setup(home = home)
            val id = first.core.createTask(newTask)
            testScheduler.runCurrent()
            first.core.sendPrompt(id, "Later")
            first.core.close()

            val second = setup(home = home)
            assertEquals(listOf(QueuedPrompt(1, "Later")), queue(second, id))
            second.core.restart(id)
            testScheduler.runCurrent()
            assertEquals(
                listOf("Later"),
                second.adapter.sessions
                    .single()
                    .prompts,
            )
            assertEquals(emptyList(), queue(second, id))
        }

    private fun queue(
        s: Setup,
        id: TaskId,
    ) = s.core.state.value[id]!!
        .task.queue
}
