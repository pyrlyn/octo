package octo.ui.vm

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import octo.core.config.ConfigEdit
import octo.core.config.ConfigFile
import octo.core.io.AtomicFile
import octo.core.paths.HostOs
import octo.core.status.AgentStatus
import octo.core.task.TaskId
import octo.core.task.TaskView
import octo.feature.common.AppOpener
import octo.testing.FakeOctoCore
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class OpenInViewModelTest {
    private val core = FakeOctoCore()
    private val config =
        ConfigFile(AtomicFile(Path(Files.createTempDirectory("octo-config").toString(), "config.json")))
    private val starts = mutableListOf<List<String>>()
    private var failure: IOException? = null
    private val opener =
        AppOpener(
            HostOs.MacOs,
            home = "/home/me",
            env = emptyMap(),
            exists = { it == "/Applications/Zed.app" },
            start = { command, _ -> failure?.let { throw it } ?: starts.add(command) },
        )

    private fun TestScope.viewModel(): Pair<OpenInViewModel, MainViewModel> {
        val main = MainViewModel(core, backgroundScope)
        val io = StandardTestDispatcher(testScheduler)
        return OpenInViewModel(opener, core, main, config, backgroundScope, io, iconOf = { null }) to main
    }

    @Test
    fun theButtonShowsOnlyForASelectedLocalTask() =
        runTest {
            core.put(TaskView(FakeOctoCore.task(TaskId("a")), AgentStatus.Idle))
            core.put(TaskView(FakeOctoCore.task(TaskId("b")).copy(machine = "box"), AgentStatus.Idle))
            val (vm, main) = viewModel()
            testScheduler.runCurrent()
            assertNull(vm.state.value, "nothing is selected")

            main.select(TaskId("b"))
            testScheduler.runCurrent()
            assertNull(vm.state.value, "a remote worktree is not on this disk")

            main.select(TaskId("a"))
            testScheduler.runCurrent()
            val state = vm.state.value!!
            assertEquals("/wt/a", state.folder)
            assertEquals(listOf("finder", "zed"), state.apps.map { it.id })
            assertEquals("finder", state.main.id, "the file manager leads until another app is used")
        }

    @Test
    fun theAppUsedLastBecomesTheButtonAndSurvivesARestart() =
        runTest {
            core.put(TaskView(FakeOctoCore.task(TaskId("a")), AgentStatus.Idle))
            val (vm, main) = viewModel()
            main.select(TaskId("a"))
            testScheduler.runCurrent()

            vm.open(
                vm.state.value!!
                    .apps
                    .single { it.id == "zed" },
            )
            vm.state.first { it?.main?.id == "zed" }

            assertEquals(listOf(listOf("open", "-a", "/Applications/Zed.app", "/wt/a")), starts)
            assertEquals(
                "zed",
                vm.state.value!!
                    .main.id,
            )
            assertEquals("zed", config.load().config.openIn)

            val (again, mainAgain) = viewModel()
            mainAgain.select(TaskId("a"))
            again.state.first { it?.main?.id == "zed" }
        }

    @Test
    fun anAppThatWillNotStartShowsAnError() =
        runTest {
            config.update(ConfigEdit.openIn("finder"))
            core.put(TaskView(FakeOctoCore.task(TaskId("a")), AgentStatus.Idle))
            val (vm, main) = viewModel()
            main.select(TaskId("a"))
            testScheduler.runCurrent()
            failure = IOException("no such app")

            vm.open(vm.state.value!!.main)
            testScheduler.runCurrent()

            assertEquals("Could not open Finder: no such app", vm.error.value)
            vm.dismissError()
            assertNull(vm.error.value)
        }
}
