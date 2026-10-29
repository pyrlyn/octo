package octo.ui.vm

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import octo.core.agent.AgentId
import octo.core.agent.PermissionMode
import octo.core.agent.VendorTerms
import octo.core.config.ConfigFile
import octo.core.io.AtomicFile
import octo.core.status.AgentStatus
import octo.core.task.TaskId
import octo.core.task.TaskView
import octo.testing.FakeCatalog
import octo.testing.FakeOctoCore
import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingViewModelTest {
    private val dir = Files.createTempDirectory("octo-ui-onboarding")
    private val path = dir.resolve("config.json")
    private val core = FakeOctoCore()

    private fun kotlinx.coroutines.CoroutineScope.onboarding() =
        OnboardingViewModel(
            ConfigFile(AtomicFile(Path(path.toString()))),
            FakeCatalog(core),
            core,
            this,
            home = "/home/me",
        )

    @Test
    fun aFirstStartWalksThroughEveryStepAndHandsOverTheFirstTask() =
        runTest {
            val vm = backgroundScope.onboarding()
            vm.state.first { it.visible }

            vm.start()
            vm.editRepo("~/src/app")
            vm.editSetup("npm ci")
            vm.confirmRepo()
            val agent = vm.state.first { it.step == OnboardingStep.Agent && it.agents != null }
            assertEquals("/home/me/src/app", agent.repo)
            assertTrue("branches /home/me/src/app" in core.calls)
            assertEquals(AgentId("demo"), agent.agent, "the installed agent is picked")

            vm.selectAgent(AgentId("missing"))
            assertEquals(AgentId("demo"), vm.state.value.agent, "an agent that is not installed cannot be picked")

            vm.confirmAgent()
            vm.selectMode(PermissionMode.Auto)
            val draft = CompletableDeferred<NewTaskDraft?>()
            vm.finish { draft.complete(it) }
            assertEquals(NewTaskDraft("/home/me/src/app", AgentId("demo"), PermissionMode.Auto), draft.await())
            assertFalse(vm.state.value.visible)
            val saved = path.readText()
            assertTrue("\"auto\"" in saved.lowercase(), saved)
            assertTrue("npm ci" in saved, saved)
        }

    @Test
    fun skippingTheRepositoryOpensNoTask() =
        runTest {
            val vm = backgroundScope.onboarding()
            vm.state.first { it.visible }
            vm.start()
            vm.skipRepo()
            vm.state.first { it.agents != null }
            vm.confirmAgent()
            val draft = CompletableDeferred<NewTaskDraft?>()
            vm.finish { draft.complete(it) }
            assertEquals(null, draft.await())
        }

    @Test
    fun pendingTermsAreShownForTheChosenAgent() =
        runTest {
            core.termsPending = VendorTerms("v1", "https://example.com/terms", "Demo terms")
            val vm = backgroundScope.onboarding()
            vm.state.first { it.visible }
            vm.skipRepo()
            vm.state.first { it.termsPending }
        }

    @Test
    fun itStaysHiddenOnceThereIsAConfigOrATask() =
        runTest {
            path.writeText("{}")
            val withConfig = backgroundScope.onboarding()
            testScheduler.advanceUntilIdle()
            assertFalse(withConfig.state.value.visible)

            Files.delete(path)
            core.put(TaskView(FakeOctoCore.task(TaskId("a"), "/src/a"), AgentStatus.Idle, emptyList()))
            val withTask = backgroundScope.onboarding()
            testScheduler.advanceUntilIdle()
            assertFalse(withTask.state.value.visible)
        }

    @Test
    fun dismissingWritesAnEmptyConfig() =
        runTest {
            val vm = backgroundScope.onboarding()
            vm.state.first { it.visible }
            vm.dismiss()
            vm.state.first { !it.visible }
            assertEquals("{}\n", path.readText())
        }
}
