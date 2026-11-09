package octo.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import octo.core.agent.ContextParts
import octo.core.agent.Role
import octo.core.agent.SessionStats
import octo.core.agent.ToolStatus
import octo.core.io.AtomicFile
import octo.core.paths.HostOs
import octo.core.status.AgentStatus
import octo.core.task.TaskId
import octo.core.task.TaskView
import octo.core.task.TranscriptEntry
import octo.core.usage.UsageLog
import octo.core.usage.UsageReport
import octo.feature.common.AppOpener
import octo.testing.FakeCatalog
import octo.testing.FakeOctoCore
import octo.ui.vm.ViewModels
import octo.ui.window.Chrome
import octo.ui.window.WindowFrame
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.nio.file.Files
import kotlin.test.Test

/**
 * Renders the main window with sample tasks for every host OS, and the macOS panes and dialogs, into the directory named by OCTO_SHOTS,
 * so a change to the shell can be compared with the designs. Skipped when it is unset.
 */
class ShellShotsTest {
    @Test
    fun rendersTheShell() {
        val out = System.getenv("OCTO_SHOTS")?.let(::File)?.apply { mkdirs() } ?: return
        val core = sampleCore()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        // The usage flag on, so the composer shows the session's tokens.
        val log =
            runBlocking {
                UsageLog.open(
                    AtomicFile(Path(Files.createTempDirectory("octo-shots").toString(), "usage.json")),
                )
            }
        // The open-in flag on, with a made-up set of apps, so the toolbar shows "Open location".
        val opener =
            AppOpener(
                HostOs.MacOs,
                home = "/home/me",
                env = emptyMap(),
                exists = { it.startsWith("/Applications/") },
                start = { _, _ -> },
            )
        // The message-map flag on, so the task screen shows the marks beside its transcript.
        val models =
            ViewModels(
                core,
                FakeCatalog(core),
                scope,
                usage = UsageReport(log, emptyMap()),
                opener = opener,
                messageMap = true,
            )
        models.main.select(TaskId("b"))
        for (os in HostOs.entries) shoot(models, os, File(out, "shell-${os.name.lowercase()}.png"))
        // The panes and dialogs are drawn for macOS alone, the one OS with its own design for them.
        models.main.openNewTask()
        shoot(models, HostOs.MacOs, File(out, "new-task-macos.png"))
        models.main.closeNewTask()
        models.main.openAgents()
        shoot(models, HostOs.MacOs, File(out, "agents-macos.png"))
        models.main.closeAgents()
        models.main.showDiff(true)
        shoot(models, HostOs.MacOs, File(out, "diff-macos.png"))
        scope.cancel()
    }

    private fun shoot(
        models: ViewModels,
        os: HostOs,
        file: File,
    ) {
        val frame = WindowFrame(os = os, chrome = if (os == HostOs.MacOs) Chrome.MacOs else Chrome.Caption)
        val scene =
            ImageComposeScene(WIDTH * SCALE, HEIGHT * SCALE, Density(SCALE.toFloat())) { OctoApp(models, frame) }
        // The view models publish on another thread, so the first frames still show the earlier state.
        repeat(SETTLE_FRAMES) {
            scene.render(it * FRAME_NANOS)
            Thread.sleep(SETTLE_MS)
        }
        file.writeBytes(scene.render(SETTLED_NANOS).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
    }

    private fun sampleCore() =
        FakeOctoCore().apply {
            put(TaskView(FakeOctoCore.task(TaskId("a"), title = "Fix flaky worktree cleanup"), AgentStatus.Idle))
            put(
                TaskView(
                    FakeOctoCore.task(TaskId("b"), title = "Add OAuth login"),
                    AgentStatus.Working,
                    stats =
                        SessionStats(
                            contextUsed = 84_000,
                            contextWindow = 200_000,
                            inputTokens = 12_400,
                            outputTokens = 6_800,
                            cacheReadTokens = 71_000,
                            cacheWriteTokens = 4_200,
                            contextParts =
                                ContextParts(
                                    cacheRead = 71_000,
                                    cacheWrite = 4_200,
                                    input = 2_000,
                                    output = 6_800,
                                ),
                        ),
                ),
            )
            put(TaskView(FakeOctoCore.task(TaskId("c"), title = "Bump Compose"), AgentStatus.Blocked))
            put(TaskView(FakeOctoCore.task(TaskId("d"), "/src/site", "Landing page copy"), AgentStatus.Stopped))
            transcripts.value =
                mapOf(
                    TaskId("b") to
                        listOf(
                            TranscriptEntry.Text(
                                Role.User,
                                "Add OAuth login with GitHub and keep the session in a cookie.",
                            ),
                            TranscriptEntry.Thought("Looking at the auth module first."),
                            TranscriptEntry.Tool(
                                "t1",
                                "./gradlew test",
                                "execute",
                                ToolStatus.Completed,
                                "BUILD SUCCESSFUL",
                            ),
                            TranscriptEntry.Text(Role.Assistant, "The login route is in place; tests pass."),
                        ),
                )
        }

    private companion object {
        const val WIDTH = 1280
        const val HEIGHT = 800
        const val SCALE = 2
        const val SETTLE_FRAMES = 5
        const val SETTLE_MS = 100L
        const val FRAME_NANOS = 16_000_000L

        // Past every enter animation, so a dialog is drawn fully opaque.
        const val SETTLED_NANOS = 5_000_000_000L
    }
}
