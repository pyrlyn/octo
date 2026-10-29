package octo.feature.diff

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import octo.core.git.FileChange
import octo.core.git.FileDiff
import octo.core.git.TaskDiff
import octo.core.status.AgentStatus
import octo.core.task.TaskId
import octo.core.task.TaskView
import octo.testing.FakeOctoCore
import octo.testing.FakeProcessLauncher
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class DiffViewModelTest {
    private val core = FakeOctoCore()
    private val id = TaskId("t1")
    private val worktree = Files.createTempDirectory("octo-diff-test").toRealPath()

    @AfterTest
    fun cleanUp() {
        worktree.toFile().deleteRecursively()
    }

    private fun file(
        path: String,
        change: FileChange = FileChange.Added,
    ) = FileDiff(path, null, change, binary = false, hunks = emptyList())

    private fun TestScope.model(
        machine: String? = null,
        previews: Boolean = true,
    ): DiffViewModel {
        val task = FakeOctoCore.task(id).copy(worktree = worktree.toString(), machine = machine)
        core.put(TaskView(task, AgentStatus.Idle))
        val previewer =
            FilePreviewer(FakeProcessLauncher(backgroundScope) {}, StandardTestDispatcher(testScheduler))
        return DiffViewModel(core, id, backgroundScope, previewer.takeIf { previews }).also {
            testScheduler.runCurrent()
        }
    }

    @Test
    fun aPreviewableFileOpensAsAPreviewAndTogglesToItsDiff() =
        runTest {
            worktree.resolve("flow.mmd").writeText("graph TD; A-->B")
            core.diffResult = TaskDiff("main", "abc", listOf(file("flow.mmd"), file("a.kt")))
            val vm = model()
            val ready = assertIs<PreviewState.Ready>(vm.state.value.preview)
            assertIs<Preview.Source>(ready.preview, "no mmdc, so the source shows")

            vm.toggleDiff()
            assertEquals(true, vm.state.value.showDiff)
            vm.select("a.kt")
            testScheduler.runCurrent()
            assertNull(vm.state.value.preview, "a Kotlin file keeps its diff")
            assertEquals(false, vm.state.value.showDiff)
        }

    @Test
    fun aFailingPreviewShowsWhy() =
        runTest {
            Files.write(worktree.resolve("broken.png"), "nope".toByteArray())
            core.diffResult = TaskDiff("main", "abc", listOf(file("broken.png")))
            assertIs<PreviewState.Failed>(model().state.value.preview)
        }

    @Test
    fun noPreviewForDeletedRemoteOrFlaggedOffFiles() =
        runTest {
            worktree.resolve("flow.mmd").writeText("graph TD; A-->B")
            core.diffResult = TaskDiff("main", "abc", listOf(file("flow.mmd")))
            assertNull(model(previews = false).state.value.preview, "the flag is off")
            assertNull(model(machine = "box").state.value.preview, "a remote task's files are not here")
            core.diffResult = TaskDiff("main", "abc", listOf(file("flow.mmd", FileChange.Deleted)))
            assertNull(model().state.value.preview, "a deleted file has nothing to show")
        }
}
