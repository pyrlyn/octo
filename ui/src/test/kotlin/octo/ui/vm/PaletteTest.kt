package octo.ui.vm

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import octo.core.agent.Role
import octo.core.status.AgentStatus
import octo.core.task.TaskId
import octo.core.task.TaskView
import octo.core.task.TranscriptEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PaletteTest {
    private val core = FakeOctoCore()

    private fun task(
        id: String,
        title: String,
        vararg messages: String,
    ) = Searchable(TaskId(id), title, messages.toList())

    private fun List<PaletteItem>.names() =
        map {
            when (it) {
                is PaletteItem.Open -> it.id.value
                is PaletteItem.Run -> it.command.name
            }
        }

    @Test
    fun tasksRankByWhereTheyMatchThenCommandsFollow() {
        val tasks =
            listOf(
                task("message", "Refactor", "please fix the diffing code"),
                task("inside", "Undiff it"),
                task("word", "Show the diff view"),
                task("prefix", "Diff colors"),
                task("none", "Login", "nothing here"),
            )
        assertEquals(
            listOf("prefix", "word", "inside", "message", "Diff"),
            rank("diff", tasks, Command.entries).names(),
        )
        val hit = rank("DIFFING", tasks, emptyList()).single() as PaletteItem.Open
        assertEquals("please fix the diffing code", hit.snippet)
        assertEquals(
            listOf("message", "inside", "word", "prefix", "none", "NewTask"),
            rank("  ", tasks, listOf(Command.NewTask)).names(),
            "an empty query lists everything in order",
        )
    }

    @Test
    fun aSnippetKeepsSomeContextOnOneLine() {
        val text = "a".repeat(100) + "\nneedle\n" + "b".repeat(100)
        val cut = snippet(text, "needle")
        assertTrue(cut.startsWith("…") && cut.endsWith("…"), cut)
        assertTrue(" needle " in cut, cut)
        assertFalse('\n' in cut)
    }

    @Test
    fun theViewModelSearchesMessagesAndDropsTaskCommandsWithoutATask() =
        runTest {
            core.put(TaskView(FakeOctoCore.task(TaskId("a"), title = "Login page"), AgentStatus.Idle))
            core.put(TaskView(FakeOctoCore.task(TaskId("b"), title = "Payments"), AgentStatus.Idle))
            core.transcripts.value =
                mapOf(
                    TaskId("b") to
                        listOf(
                            TranscriptEntry.Text(Role.User, "Fix the login redirect"),
                            TranscriptEntry.Notice("login notice is not a message"),
                        ),
                )
            val vm = PaletteViewModel(core, backgroundScope, Command.entries, selected = null)
            vm.edit("login")
            testScheduler.runCurrent()
            assertEquals(
                listOf("a", "b"),
                vm.state.value.items
                    .names(),
            )

            vm.move(5)
            assertEquals(1, vm.state.value.selected, "the highlight stops at the end")
            assertEquals(PaletteItem.Open(TaskId("b"), "Payments", "Fix the login redirect"), vm.chosen())
            vm.move(-5)
            assertEquals(0, vm.state.value.selected)

            vm.edit("")
            testScheduler.runCurrent()
            val commands =
                vm.state.value.items
                    .filterIsInstance<PaletteItem.Run>()
                    .map { it.command }
            assertEquals(listOf(Command.NewTask, Command.Agents, Command.Devices), commands)
        }

    @Test
    fun publishNeedsALocalTask() =
        runTest {
            val remote = FakeOctoCore.task(TaskId("r")).copy(machine = "box")
            val vm = PaletteViewModel(core, backgroundScope, listOf(Command.Publish, Command.Stop), remote)
            testScheduler.runCurrent()
            assertEquals(
                listOf("Stop"),
                vm.state.value.items
                    .names(),
            )
        }

    @Test
    fun commandsBehindAnOffFlagAreLeftOut() =
        runTest {
            val catalog = FakeCatalog(core)
            assertNull(ViewModels(core, catalog, backgroundScope).palette())
            core.put(TaskView(FakeOctoCore.task(TaskId("a")), AgentStatus.Idle))
            val models = ViewModels(core, catalog, backgroundScope, fork = true, palette = true)
            models.main.select(TaskId("a"))
            testScheduler.runCurrent()
            val vm = models.palette()!!
            testScheduler.runCurrent()
            assertEquals(
                listOf("a", "NewTask", "Diff", "Fork", "Stop", "Agents"),
                vm.state.value.items
                    .names(),
            )
        }

    @Test
    fun theWindowRunsACommand() =
        runTest {
            core.put(TaskView(FakeOctoCore.task(TaskId("a")), AgentStatus.Idle))
            val main = MainViewModel(core, backgroundScope)
            main.run(Command.Stop)
            testScheduler.runCurrent()
            assertTrue(core.stopped.isEmpty(), "nothing to stop without a selected task")

            main.select(TaskId("a"))
            testScheduler.runCurrent()
            main.run(Command.Publish)
            assertEquals(TaskDialog.Publish, main.state.value.taskDialog)
            main.run(Command.Diff)
            testScheduler.runCurrent()
            assertTrue(main.state.value.diffOpen)
            main.run(Command.Stop)
            main.run(Command.Devices)
            testScheduler.runCurrent()
            assertEquals(listOf(TaskId("a")), core.stopped)
            assertTrue(main.state.value.devicesOpen)

            core.put(TaskView(FakeOctoCore.task(TaskId("b")), AgentStatus.Idle))
            main.select(TaskId("b"))
            assertNull(main.state.value.taskDialog, "another task closes the dialog")
            main.togglePalette()
            assertTrue(main.state.value.paletteOpen)
            main.closePalette()
            assertFalse(main.state.value.paletteOpen)
        }
}
