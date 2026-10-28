package octo.ui.vm

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import octo.core.agent.AgentId
import octo.core.agent.VendorTerms
import octo.core.preset.Preset
import octo.core.preset.PresetLibrary
import octo.core.preset.PresetMember
import octo.core.task.TaskId
import octo.testing.FakeCatalog
import octo.testing.FakeOctoCore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PresetViewModelTest {
    private class MemoryLibrary(
        vararg initial: Preset,
    ) : PresetLibrary {
        override val presets = MutableStateFlow(initial.toList())

        override suspend fun put(
            preset: Preset,
            replacing: String?,
        ) = presets.update { all -> all.filter { it.name != (replacing ?: preset.name) } + preset }

        override suspend fun remove(name: String) = presets.update { all -> all.filter { it.name != name } }
    }

    private val core = FakeOctoCore()
    private val pair =
        Preset(
            "Pair",
            listOf(PresetMember("Coder", AgentId("demo"), "Build it"), PresetMember("Reviewer", AgentId("demo"))),
        )

    private fun TestScope.settle() = testScheduler.runCurrent()

    private fun TestScope.loadBranches() {
        testScheduler.advanceTimeBy(NewTaskUiState.BRANCH_LOOKUP_DELAY_MS)
        settle()
    }

    @Test
    fun teamsAreHiddenWithoutALibrary() =
        runTest {
            val vm = NewTaskViewModel(core, FakeCatalog(core), backgroundScope)
            settle()
            assertFalse(vm.state.value.presetsEnabled)
            vm.useTeam(true)
            assertFalse(vm.state.value.team)
        }

    @Test
    fun aTeamStartsEveryMemberAfterTheTerms() =
        runTest {
            core.termsPending = VendorTerms("1", "Read these", "https://example.com/terms")
            val catalog = FakeCatalog(core)
            val vm = NewTaskViewModel(core, catalog, backgroundScope, MemoryLibrary(pair))
            settle()
            vm.useTeam(true)
            vm.editRepo("/src/octo")
            loadBranches()
            assertFalse(vm.state.value.canCreate, "no preset chosen yet")
            vm.selectPreset("Pair")
            assertTrue(vm.state.value.canCreate)

            vm.create()
            settle()
            assertNotNull(vm.state.value.terms)
            assertTrue(core.calls.none { it.startsWith("create") }, "nothing starts before the terms are settled")

            vm.acceptTerms()
            settle()
            assertEquals(listOf(AgentId("demo")), catalog.acknowledged)
            assertEquals(TaskId("t1"), vm.state.value.created)
            val creates = core.calls.filter { it.startsWith("create") }
            assertEquals(2, creates.size)
            assertTrue(creates[0].startsWith("create Pair: Coder You are the Coder"), creates[0])
            assertTrue(creates[1].startsWith("create Pair: Reviewer You are the Reviewer"), creates[1])
        }

    @Test
    fun aTeamWithAnAgentThatIsNotInstalledCannotStart() =
        runTest {
            val broken = Preset("Broken", listOf(PresetMember("Lead", AgentId("missing"))))
            val vm = NewTaskViewModel(core, FakeCatalog(core), backgroundScope, MemoryLibrary(broken))
            settle()
            vm.useTeam(true)
            vm.editRepo("/src/octo")
            loadBranches()
            vm.selectPreset("Broken")
            assertEquals(listOf(AgentId("missing")), vm.state.value.missingAgents)
            assertFalse(vm.state.value.canCreate)
        }

    @Test
    fun theEditorCreatesEditsAndDeletesPresets() =
        runTest {
            val library = MemoryLibrary(pair)
            val vm = PresetsViewModel(library, FakeCatalog(core), backgroundScope)
            settle()

            vm.create()
            assertEquals(
                AgentId("demo"),
                vm.state.value.draft!!
                    .members
                    .single()
                    .agent,
                "the installed agent is the default",
            )
            assertFalse(vm.state.value.canSave, "a preset needs a name")
            vm.editName("Solo")
            vm.editPrompt(0, "Go")
            vm.addMember()
            vm.editRole(1, "Lead")
            assertFalse(vm.state.value.canSave, "roles must differ")
            vm.removeMember(1)
            vm.save()
            settle()
            assertNull(vm.state.value.draft)
            assertEquals(listOf("Pair", "Solo"), library.presets.value.map { it.name })

            vm.edit("Pair")
            vm.editName("Duo")
            vm.selectAgent(1, AgentId("missing"))
            vm.save()
            settle()
            val duo = library.presets.value.single { it.name == "Duo" }
            assertEquals(AgentId("missing"), duo.members[1].agent)
            assertTrue(library.presets.value.none { it.name == "Pair" })

            vm.delete("Solo")
            settle()
            assertEquals(
                listOf("Duo"),
                vm.state.value.presets
                    .map { it.name },
            )
        }
}
