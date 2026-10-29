package octo.ui.vm

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import octo.core.flags.CoreFlags
import octo.core.flags.FeatureFlags
import octo.core.plugins.PluginLoader
import octo.core.status.AgentStatus
import octo.core.task.TaskId
import octo.core.task.TaskView
import octo.feature.task.PluginActionsViewModel
import octo.feature.task.PluginMessage
import octo.testing.FakeCatalog
import octo.testing.FakeOctoCore
import wasm.chasm.ChasmRuntime
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PluginActionsViewModelTest {
    private val id = TaskId("t1")
    private val core = FakeOctoCore().apply { put(TaskView(FakeOctoCore.task(id), AgentStatus.Idle)) }

    // The demo plugin built by core/src/commonTest/fixtures/plugins/build.sh.
    private val plugins =
        runBlocking {
            val dir = Files.createTempDirectory("octo-ui-plugins").toFile()
            File("../core/src/commonTest/fixtures/plugins/demo.wasm").copyTo(File(dir, "demo.wasm"))
            PluginLoader.load(FeatureFlags(mapOf(CoreFlags.PLUGINS.id to true)), Path(dir.path), ChasmRuntime).plugins
        }

    @Test
    fun listsActionsSendsPromptsAndShowsMessages() =
        runTest {
            val vm = PluginActionsViewModel(plugins, core, id, backgroundScope)
            val actions = vm.state.value.actions
            assertEquals(listOf("Demo: Ask for a review", "Demo: Show a note"), actions.map { it.title })

            vm.run(actions[0])
            vm.state.first { !it.running }
            assertEquals(listOf("prompt Review the changes on octo/t1."), core.calls)

            vm.run(actions[1])
            val shown = vm.state.first { it.message != null }.message
            assertEquals(PluginMessage("Demo", "Hello from the demo plugin about Task t1."), shown)
            vm.dismissMessage()
            assertEquals(null, vm.state.value.message)
        }

    @Test
    fun aRemovedTaskShowsAnError() =
        runTest {
            val vm = PluginActionsViewModel(plugins, core, TaskId("gone"), backgroundScope)
            vm.run(vm.state.value.actions[0])
            assertNotNull(vm.state.first { it.error != null }.error)
            assertEquals(emptyList(), core.calls)
        }

    @Test
    fun noActionsMeansNoMenu() {
        val models =
            ViewModels(
                core,
                FakeCatalog(core),
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            )
        assertEquals(null, models.pluginActions(id))
    }
}
