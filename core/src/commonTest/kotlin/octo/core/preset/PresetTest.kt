package octo.core.preset

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import octo.core.agent.AgentId
import octo.core.io.AtomicFile
import octo.core.io.ReadOnlyFileException
import octo.core.orchestrator.setup
import octo.core.tempDir
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class PresetTest {
    private val pair =
        Preset(
            "Pair",
            listOf(
                PresetMember("Coder", AgentId("claude"), "Build the parser"),
                PresetMember("Reviewer", AgentId("codex")),
            ),
        )

    private fun file() = AtomicFile(Path(tempDir("octo-presets"), "presets.json"))

    @Test
    fun presetsRoundTripThroughTheFile() =
        runTest {
            val file = file()
            val library = FilePresetLibrary.open(file)
            assertNull(library.warning)
            assertEquals(emptyList(), library.presets.value)

            library.put(pair)
            library.put(Preset("  Solo ", listOf(PresetMember("Lead", AgentId("cursor")))))
            library.put(pair.copy(name = "Duo"), replacing = "Pair")
            assertFailsWith<PresetException> { library.put(pair.copy(name = "Solo"), replacing = "Duo") }

            val again = FilePresetLibrary.open(file)
            assertEquals(listOf("Duo", "Solo"), again.presets.value.map { it.name })
            assertEquals(pair.members, again.presets.value[0].members)

            again.remove("Duo")
            assertEquals(
                listOf("Solo"),
                FilePresetLibrary
                    .open(file)
                    .presets.value
                    .map { it.name },
            )
        }

    @Test
    fun aBrokenFileIsNeverOverwritten() =
        runTest {
            val file = file()
            file.write("{not json")
            val library = FilePresetLibrary.open(file)
            assertNotNull(library.warning)
            assertEquals(emptyList(), library.presets.value)
            assertFailsWith<ReadOnlyFileException> { library.put(pair) }
            assertEquals("{not json", file.read())
        }

    @Test
    fun invalidPresetsAreRefused() =
        runTest {
            val library = FilePresetLibrary.open(file())
            val member = PresetMember("Lead", AgentId("claude"))
            listOf(
                Preset(" ", listOf(member)),
                Preset("Empty", emptyList()),
                Preset("No role", listOf(member.copy(role = ""))),
                Preset("No agent", listOf(member.copy(agent = AgentId("")))),
                Preset("Twice", listOf(member, member.copy(role = " Lead "))),
            ).forEach { preset ->
                assertNotNull(preset.problem(), preset.name)
                assertFailsWith<PresetException>(preset.name) { library.put(preset) }
            }
            assertEquals(emptyList(), library.presets.value)
        }

    @Test
    fun startingAPresetCreatesOneTaskPerMember() =
        runTest {
            var next = 0
            val s = setup(newId = { "id${++next}" })
            val team = pair.copy(members = pair.members.map { it.copy(agent = AgentId("fake")) })
            val ids = s.core.startPreset(team, "/src/app", "main")
            testScheduler.runCurrent()

            assertEquals(2, ids.size)
            val tasks =
                ids.map {
                    s.core.state.value[it]!!
                        .task
                }
            assertEquals(listOf("Pair: Coder", "Pair: Reviewer"), tasks.map { it.title })
            val prompts = s.adapter.sessions.map { it.prompts.single() }
            assertEquals(
                "You are the Coder in the Octo team \"Pair\". Your teammates, each in its own task and worktree: " +
                    "Reviewer (fake).\n\nBuild the parser",
                prompts[0],
            )
            assertEquals(
                "You are the Reviewer in the Octo team \"Pair\". Your teammates, each in its own task and worktree: " +
                    "Coder (fake).",
                prompts[1],
            )
        }

    @Test
    fun aLoneMemberGetsJustItsPrompt() {
        val solo = Preset("Solo", listOf(PresetMember("Lead", AgentId("claude"), " Go ")))
        assertEquals("Go", firstPrompt(solo, solo.members.single()))
        assertNull(
            firstPrompt(
                solo.copy(members = listOf(solo.members.single().copy(prompt = ""))),
                PresetMember("x", AgentId("y")),
            ),
        )
    }
}
