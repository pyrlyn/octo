package octo.core.orchestrator

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import octo.core.agent.AgentEvent
import octo.core.agent.AgentId
import octo.core.agent.Capability
import octo.core.agent.Question
import octo.core.agent.RequestResponse
import octo.core.agent.StopReason
import octo.core.task.Attachment
import octo.core.task.NewAttachment
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
class AttachmentsTest {
    private val png = NewAttachment("shot.png", "image/png", byteArrayOf(1, 2, 3))
    private val log = NewAttachment("../build log.txt", "text/plain", "boom".encodeToByteArray())

    private suspend fun TestScope.task(s: Setup): TaskId {
        val id = s.core.createTask(NewTask("/src/app", "main", AgentId("fake"), "Fix"))
        testScheduler.runCurrent()
        return id
    }

    private suspend fun Setup.userTexts(id: TaskId) =
        core.transcript(id).first().filterIsInstance<TranscriptEntry.Text>()

    private fun exists(path: String) = SystemFileSystem.exists(Path(path))

    private fun Setup.folder(id: TaskId) = Path(home, "attachments", id.value)

    @Test
    fun filesAreSavedInTheTaskFolderAndTheAgentGetsTheirPaths() =
        runTest {
            val s = setup()
            val id = task(s)

            s.core.sendPrompt(id, "Why does it fail?", listOf(png, log))
            val sent = s.userTexts(id).single()
            assertEquals("Why does it fail?", sent.text)
            assertEquals(listOf("shot.png", "../build log.txt"), sent.attachments.map { it.name })
            sent.attachments.forEach {
                assertEquals(s.folder(id).toString(), Path(it.path).parent.toString())
                assertTrue(exists(it.path), it.path)
            }
            assertTrue(sent.attachments[1].path.endsWith("-build_log.txt"), "the name is made safe")
            val saved = SystemFileSystem.source(Path(sent.attachments[0].path)).buffered().use { it.readByteArray() }
            assertContentEquals(png.bytes, saved)

            val session = s.adapter.sessions.single()
            val prompt = session.prompts.single()
            assertTrue(prompt.startsWith("Why does it fail?\n\nAttached files"), prompt)
            sent.attachments.forEach { assertTrue("- ${it.path}" in prompt, prompt) }
            assertEquals(listOf(emptyList()), session.images, "an agent without Images gets paths only")
        }

    @Test
    fun theFirstPromptOfANewTaskTakesFilesToo() =
        runTest {
            val s = setup()
            val id = s.core.createTask(NewTask("/src/app", "main", AgentId("fake"), "Fix", attachments = listOf(log)))
            testScheduler.runCurrent()

            val sent = s.userTexts(id).single()
            assertEquals("", sent.text, "files alone are a message")
            val file = sent.attachments.single()
            assertTrue(exists(file.path))
            assertTrue(
                "- ${file.path}" in
                    s.adapter.sessions
                        .single()
                        .prompts
                        .single(),
            )
        }

    @Test
    fun anAgentWithImagesGetsThePicturesToo() =
        runTest {
            val s = setup(adapter = FakeAdapter(capabilities = setOf(Capability.Resume, Capability.Images)))
            val id = task(s)

            s.core.sendPrompt(id, "Look", listOf(png, log))
            val image =
                s.adapter.sessions
                    .single()
                    .images
                    .single()
                    .single()
            assertEquals("image/png", image.mimeType)
            assertContentEquals(png.bytes, image.data)
            assertEquals(
                s
                    .userTexts(id)
                    .single()
                    .attachments[0]
                    .path,
                image.path,
            )
        }

    @Test
    fun aQueuedPromptKeepsItsFilesAndRemovingItDeletesThem() =
        runTest {
            val s = setup()
            val id = task(s)
            val session = s.adapter.sessions.single()
            s.core.sendPrompt(id, "First")

            s.core.sendPrompt(id, "Later", listOf(log))
            s.core.sendPrompt(id, "Dropped", listOf(png))
            val queue =
                s.core.state.value[id]!!
                    .task.queue
            val dropped = queue[1].attachments.single()
            s.core.removeQueued(id, queue[1].id)
            assertFalse(exists(dropped.path), "a removed message takes its files along")

            session.emit(AgentEvent.TurnStarted, AgentEvent.TurnEnded(StopReason.EndTurn))
            testScheduler.runCurrent()
            val later = queue[0].attachments.single()
            assertTrue("- ${later.path}" in session.prompts.last(), session.prompts.last())
            assertEquals(listOf(later), s.userTexts(id).last().attachments)
        }

    @Test
    fun anAnswerCarriesThePathsOfItsFiles() =
        runTest {
            val s = setup()
            val id = task(s)
            val session = s.adapter.sessions.single()
            session.emit(AgentEvent.QuestionAsked("q", listOf(Question("why", "What broke?"))))
            testScheduler.runCurrent()

            assertFailsWith<IllegalArgumentException> {
                s.core.respond(id, "q", RequestResponse.Cancelled, mapOf("why" to listOf(log)))
            }
            s.core.respond(id, "q", RequestResponse.Answers(mapOf("why" to "This")), mapOf("why" to listOf(log)))
            val answer = (session.responses.single().second as RequestResponse.Answers).answers.getValue("why")
            val request =
                s.core
                    .transcript(id)
                    .first()
                    .filterIsInstance<TranscriptEntry.Request>()
                    .single()
            val file = request.attachments.single()
            assertTrue(answer.startsWith("This\n\nAttached files") && "- ${file.path}" in answer, answer)
            assertTrue(exists(file.path))
        }

    @Test
    fun aRevertDropsTheFilesOfDiscardedMessages() =
        runTest {
            val s = setup(checkpoints = true)
            val id = task(s)
            val session = s.adapter.sessions.single()

            suspend fun turn(
                text: String,
                files: List<NewAttachment>,
            ) {
                s.core.sendPrompt(id, text, files)
                session.emit(AgentEvent.TurnEnded(StopReason.EndTurn))
                testScheduler.runCurrent()
            }
            turn("Keep", listOf(log))
            turn("Discard", listOf(png))
            val (kept, discarded) = s.userTexts(id).map { it.attachments.single() }

            s.core.revert(
                id,
                s.core.state.value[id]!!
                    .task.checkpoints[1]
                    .ref,
            )
            assertTrue(exists(kept.path))
            assertFalse(exists(discarded.path))
            assertEquals(listOf(listOf(kept), emptyList<Attachment>()), s.userTexts(id).map { it.attachments })
        }

    @Test
    fun removingTheTaskRemovesItsFolder() =
        runTest {
            val s = setup()
            val id = task(s)
            s.core.sendPrompt(id, "Look", listOf(png))
            assertTrue(SystemFileSystem.exists(s.folder(id)))

            s.core.removeTask(id, deleteWorktree = false)
            assertFalse(SystemFileSystem.exists(s.folder(id)))
        }

    @Test
    fun attachmentsSurviveARestart() =
        runTest {
            val first = setup()
            val id = task(first)
            first.core.sendPrompt(id, "Look", listOf(png))
            val before = first.userTexts(id).single().attachments
            first.core.close()

            val second = setup(home = first.home)
            assertEquals(before, second.userTexts(id).single().attachments)
        }
}
