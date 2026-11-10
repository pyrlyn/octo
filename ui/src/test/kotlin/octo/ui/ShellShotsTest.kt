package octo.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import octo.adapters.claude.ClaudeAdapter
import octo.adapters.codex.CodexAdapter
import octo.core.agent.AgentId
import octo.core.agent.ContextParts
import octo.core.agent.PlanEntry
import octo.core.agent.Role
import octo.core.agent.SessionStats
import octo.core.agent.ToolStatus
import octo.core.io.AtomicFile
import octo.core.paths.HostOs
import octo.core.status.AgentStatus
import octo.core.task.Task
import octo.core.task.TaskId
import octo.core.task.TaskView
import octo.core.task.TranscriptEntry
import octo.core.usage.UsageLog
import octo.core.usage.UsageReport
import octo.feature.common.AppOpener
import octo.testing.FakeCatalog
import octo.testing.FakeOctoCore
import octo.testing.FakeProcessLauncher
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
        // The adapters are only asked for their glyphs; nothing is launched.
        val launcher = FakeProcessLauncher(scope) {}
        val icons = listOf(ClaudeAdapter(launcher), CodexAdapter(launcher)).associate { it.id to it.icon!! }
        // The open-in flag on, with a made-up set of apps, so the toolbar shows "Open location".
        val opener =
            AppOpener(
                HostOs.MacOs,
                home = "/home/me",
                env = emptyMap(),
                exists = { it.startsWith("/Applications/") },
                start = { _, _ -> },
            )
        // The history-rail flag on, so the task screen shows its rail.
        val models =
            ViewModels(
                core,
                FakeCatalog(core, icons),
                scope,
                usage = UsageReport(log, emptyMap()),
                opener = opener,
                historyRail = true,
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
            put(
                TaskView(
                    FakeOctoCore.task(TaskId("a"), title = "Fix flaky worktree cleanup").by("codex"),
                    AgentStatus.Idle,
                ),
            )
            put(
                TaskView(
                    FakeOctoCore.task(TaskId("b"), title = "Add OAuth login").by("claude"),
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
            put(TaskView(FakeOctoCore.task(TaskId("c"), title = "Bump Compose").by("claude"), AgentStatus.Blocked))
            put(TaskView(FakeOctoCore.task(TaskId("d"), "/src/site", "Landing page copy"), AgentStatus.Stopped))
            transcripts.value = mapOf(TaskId("b") to sampleTranscript())
        }

    private fun sampleTranscript() =
        listOf(
            TranscriptEntry.Text(
                Role.User,
                "Add OAuth login with GitHub and keep the session in a cookie.",
                atMs = System.currentTimeMillis() - HOUR_MS,
            ),
            TranscriptEntry.Thought("Looking at the auth module first."),
            TranscriptEntry.Plan(
                listOf(
                    PlanEntry("Read the auth module", done = true),
                    PlanEntry("Add the login route", done = true),
                    PlanEntry("Run the tests", done = false),
                    PlanEntry("Write the session cookie", done = false),
                ),
            ),
            TranscriptEntry.Tool(
                "t1",
                "./gradlew test",
                "execute",
                ToolStatus.Completed,
                "BUILD SUCCESSFUL",
            ),
            TranscriptEntry.Text(Role.Assistant, "The login route is in place; tests pass."),
            // A second turn, so the history rail shows.
            TranscriptEntry.Text(
                Role.User,
                "Add a logout button to the header.",
                atMs = System.currentTimeMillis() - MINUTE_MS,
            ),
            TranscriptEntry.Text(Role.Assistant, "The header has a Log out button now."),
        )

    // Task "d" stays on the demo agent, which has no glyph here, so the renders show the monogram too.
    private fun Task.by(agent: String) = copy(agent = AgentId(agent))

    private companion object {
        const val WIDTH = 1280
        const val HEIGHT = 800
        const val SCALE = 2
        const val SETTLE_FRAMES = 5
        const val SETTLE_MS = 100L
        const val FRAME_NANOS = 16_000_000L
        const val MINUTE_MS = 60_000L
        const val HOUR_MS = 60 * MINUTE_MS

        // Past every enter animation, so a dialog is drawn fully opaque.
        const val SETTLED_NANOS = 5_000_000_000L
    }
}
