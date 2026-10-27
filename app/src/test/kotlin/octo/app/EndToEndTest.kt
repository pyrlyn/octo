package octo.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.files.Path
import octo.adapters.demo.DemoAdapter
import octo.core.agent.Role
import octo.core.git.Git
import octo.core.paths.OctoPaths
import octo.core.process.JvmProcessLauncher
import octo.core.status.AgentStatus
import octo.core.task.PendingRequest
import octo.core.task.TranscriptEntry
import octo.ui.vm.ViewModels
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The whole MVP path through the view models, with the real core, real git and the demo agent. */
class EndToEndTest {
    private val root = Files.createTempDirectory("octo-e2e").toFile().canonicalFile
    private val home = File(root, "home").apply { mkdirs() }
    private val octoHome = File(root, "octo").apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // git must never read the user's real configuration or identity.
    private val launcher =
        JvmProcessLauncher(
            mapOf(
                "PATH" to (System.getenv("PATH") ?: ""),
                "HOME" to home.path,
                "XDG_CONFIG_HOME" to File(home, ".config").path,
                "GIT_CONFIG_GLOBAL" to File(home, "gitconfig").apply { writeText("") }.path,
                "GIT_CONFIG_NOSYSTEM" to "1",
                "GIT_AUTHOR_NAME" to "Octo Test",
                "GIT_AUTHOR_EMAIL" to "test@octo.invalid",
                "GIT_COMMITTER_NAME" to "Octo Test",
                "GIT_COMMITTER_EMAIL" to "test@octo.invalid",
            ),
        )

    @AfterTest
    fun cleanUp() {
        scope.cancel()
        root.deleteRecursively()
    }

    private suspend fun <T> StateFlow<T>.until(predicate: (T) -> Boolean): T =
        withTimeout(TIMEOUT_MS) { first(predicate) }

    @Test
    fun createAnswerReviewAndStop() =
        runBlocking {
            val repo = File(root, "repo").apply { mkdirs() }
            val git = Git(launcher)
            git.run(repo.path, listOf("init", "-q", "-b", "main"))
            File(repo, "README.md").writeText("hello\n")
            git.run(repo.path, listOf("add", "."))
            git.run(repo.path, listOf("commit", "-q", "-m", "init"))
            File(octoHome, "config.json").writeText("""{"flags": {"${DemoAdapter.FLAG.id}": true}}""")

            val octo = Octo.open(OctoPaths(Path(octoHome.path)), launcher, scope)
            val models = ViewModels(octo.core, octo.catalog, scope, octo.warnings, octo.publisher)

            val form = models.newTask()
            form.state.until { !it.loadingAgents }
            form.selectAgent(DemoAdapter.ID)
            form.editRepo(repo.path)
            form.state.until { it.baseRef in it.branches }
            form.editPrompt("Say hello")
            form.create()
            val id = form.state.until { it.created != null || it.error != null }.let { it.created ?: error(it.error!!) }
            models.main.created(id)
            assertEquals(
                id,
                models.main.state
                    .until { it.selected != null }
                    .selected,
            )

            val task = models.task(id)
            val request =
                task.state
                    .until { it.pending.isNotEmpty() }
                    .pending
                    .single() as PendingRequest.Permission
            assertEquals(AgentStatus.Blocked, task.state.value.status)
            task.choose(request.id, "allow")
            val done = task.state.until { s -> s.status == AgentStatus.Idle && s.transcript.any { it.isReply() } }
            assertTrue(
                done.transcript.first() == TranscriptEntry.Text(Role.User, "Say hello"),
                done.transcript.toString(),
            )

            val diff = models.diff(id)
            val loaded = diff.state.until { !it.loading }
            assertEquals(listOf(DemoAdapter.FILE_NAME), loaded.files.map { it.path }, loaded.error)
            assertEquals(1, loaded.files.single().additions)

            task.stop()
            assertTrue(task.state.until { it.status == AgentStatus.Stopped }.canRestart)
            octo.close()
        }

    private fun TranscriptEntry.isReply() =
        this is TranscriptEntry.Text && role == Role.Assistant && !partial && text.contains(DemoAdapter.FILE_NAME)

    private companion object {
        const val TIMEOUT_MS = 20_000L
    }
}
