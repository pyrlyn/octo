package octo.core.orchestrator

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import octo.core.agent.AgentEvent
import octo.core.agent.AgentId
import octo.core.agent.Capability
import octo.core.agent.Role
import octo.core.agent.StopReason
import octo.core.attachments.Attachment
import octo.core.attachments.Attachments
import octo.core.attachments.FileUpload
import octo.core.task.NewTask
import octo.core.task.TaskId
import octo.core.task.TranscriptEntry
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AttachmentTest {
    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 1, 2)
    private val log = "boom".encodeToByteArray()

    private suspend fun TestScope.task(
        s: Setup,
        request: NewTask = NewTask("/src/app", "main", AgentId("fake"), "Fix"),
    ): TaskId {
        val id = s.core.createTask(request)
        testScheduler.runCurrent()
        return id
    }

    private suspend fun Setup.userEntry(id: TaskId) =
        core
            .transcript(id)
            .first()
            .filterIsInstance<TranscriptEntry.Text>()
            .last { it.role == Role.User }

    private fun exists(path: String) = SystemFileSystem.exists(Path(path))

    @Test
    fun anAgentThatTakesImagesGetsTheImageAndThePathsOfTheRest() =
        runTest {
            val s = setup(FakeAdapter(setOf(Capability.Images)))
            val id = task(s)

            s.core.sendPrompt(id, "Why?", listOf(FileUpload("shot.png", png), FileUpload("../app.log", log)))

            val session = s.adapter.sessions.single()
            val attachments = s.userEntry(id).attachments
            assertEquals(listOf("shot.png", "app.log"), attachments.map { it.name })
            assertEquals(listOf("image/png", Attachments.FALLBACK_TYPE), attachments.map { it.mimeType })
            val (image, file) = attachments
            assertTrue(image.path.startsWith(Path(s.home, "attachments", id.value).toString()))
            assertContentEquals(png, session.images.single().data)
            assertEquals("Why?\n\nAttached files:\n- ${file.path}", session.prompts.single())
            assertTrue(exists(image.path) && exists(file.path))
        }

    @Test
    fun anAgentWithoutImagesReadsEveryPath() =
        runTest {
            val s = setup()
            val id = task(s)

            s.core.sendPrompt(id, "", listOf(FileUpload("shot.png", png)))

            val session = s.adapter.sessions.single()
            val path =
                s
                    .userEntry(id)
                    .attachments
                    .single()
                    .path
            assertEquals(listOf("Attached files:\n- $path"), session.prompts)
            assertTrue(session.images.isEmpty())
        }

    @Test
    fun theFirstPromptCarriesItsFiles() =
        runTest {
            val s = setup()
            val id =
                task(s, NewTask("/src/app", "main", AgentId("fake"), "Fix", "Look", listOf(FileUpload("a.log", log))))

            val path =
                s
                    .userEntry(id)
                    .attachments
                    .single()
                    .path
            assertEquals(
                listOf("Look\n\nAttached files:\n- $path"),
                s.adapter.sessions
                    .single()
                    .prompts,
            )
        }

    @Test
    fun tooManyFilesAreRefusedBeforeAnyIsSaved() =
        runTest {
            val s = setup()
            val id = task(s)
            val files = List(Attachments.MAX_FILES + 1) { FileUpload("f$it.txt", log) }

            assertFailsWith<IllegalArgumentException> { s.core.sendPrompt(id, "Go", files) }

            assertFalse(exists(Path(s.home, "attachments").toString()))
            assertTrue(
                s.adapter.sessions
                    .single()
                    .prompts
                    .isEmpty(),
            )
        }

    @Test
    fun aQueuedMessageKeepsItsFilesUntilItIsSent() =
        runTest {
            val s = setup()
            val id = task(s)
            s.core.sendPrompt(id, "First")
            s.core.sendPrompt(id, "Second", listOf(FileUpload("a.log", log)))
            val queued =
                s.core.state.value[id]!!
                    .task.queue
                    .single()
            assertEquals("a.log", queued.attachments.single().name)

            s.adapter.sessions
                .single()
                .emit(AgentEvent.TurnEnded(StopReason.EndTurn))
            testScheduler.runCurrent()

            val path = queued.attachments.single().path
            assertEquals(
                "Second\n\nAttached files:\n- $path",
                s.adapter.sessions
                    .single()
                    .prompts
                    .last(),
            )
            assertEquals(queued.attachments, s.userEntry(id).attachments)
        }

    @Test
    fun removingAQueuedMessageDeletesItsFiles() =
        runTest {
            val s = setup()
            val id = task(s)
            s.core.sendPrompt(id, "First")
            s.core.sendPrompt(id, "Second", listOf(FileUpload("a.log", log)))
            val queued =
                s.core.state.value[id]!!
                    .task.queue
                    .single()

            s.core.removeQueued(id, queued.id)

            assertFalse(exists(queued.attachments.single().path))
        }

    @Test
    fun removingTheTaskDeletesItsFiles() =
        runTest {
            val s = setup()
            val id = task(s)
            val saved = s.core.attach(id, listOf(FileUpload("a.log", log)))
            assertTrue(exists(saved.single().path))

            s.core.removeTask(id, deleteWorktree = true)

            assertFalse(exists(Path(s.home, "attachments", id.value).toString()))
        }

    @Test
    fun aRevertDeletesTheFilesOfLaterMessagesAndMarksThem() =
        runTest {
            val s = setup(checkpoints = true)
            val id = task(s)
            val session = s.adapter.sessions.single()
            s.core.sendPrompt(id, "Go", listOf(FileUpload("early.log", log)))
            session.emit(AgentEvent.TurnEnded(StopReason.EndTurn))
            testScheduler.runCurrent()
            s.core.sendPrompt(id, "More", listOf(FileUpload("late.log", log)))
            session.emit(AgentEvent.TurnEnded(StopReason.EndTurn))
            testScheduler.runCurrent()
            val refs =
                s.core.state.value[id]!!
                    .task.checkpoints
                    .map { it.ref }

            s.core.revert(id, refs[1])

            val texts =
                s.core
                    .transcript(id)
                    .first()
                    .filterIsInstance<TranscriptEntry.Text>()
            val (early, late) = texts.map(TranscriptEntry.Text::attachments).map(List<Attachment>::single)
            assertFalse(early.removed)
            assertTrue(exists(early.path))
            assertTrue(late.removed)
            assertFalse(exists(late.path))
        }
}
