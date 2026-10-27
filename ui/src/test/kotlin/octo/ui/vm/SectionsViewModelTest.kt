package octo.ui.vm

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import octo.core.status.AgentStatus
import octo.core.task.TaskGroup
import octo.core.task.TaskId
import octo.core.task.TaskPlace
import octo.core.task.TaskView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SectionsViewModelTest {
    private val core = FakeOctoCore()

    private fun put(
        id: String,
        place: TaskPlace = TaskPlace.Active,
        parent: String? = null,
        group: TaskGroup? = null,
    ) = core.put(
        TaskView(
            FakeOctoCore
                .task(TaskId(id))
                .copy(place = place, parentId = parent?.let(::TaskId), group = group),
            AgentStatus.Idle,
        ),
    )

    private fun MainViewModel.rows() =
        state.value.groups
            .single()
            .tasks
            .map { listOfNotNull(it.sectionTitle, it.id.value).joinToString(": ") }

    @Test
    fun tasksSitInTheirSectionsWithTheirForks() =
        runTest {
            val group = TaskGroup("g", "Compare")
            put("a")
            put("s", TaskPlace.Settled)
            put("p", TaskPlace.Pinned)
            put("p1", parent = "p")
            put("z", TaskPlace.Snoozed(untilMs = 5))
            put("g1", TaskPlace.Pinned, group = group)
            put("g2", group = group)
            val main = MainViewModel(core, backgroundScope, sections = true)
            testScheduler.runCurrent()
            assertEquals(
                listOf("Pinned: p", "p1", "g1", "g2", "Active: a", "Snoozed: z", "Settled: s"),
                main.rows(),
                "a fork follows its parent and a comparison its first task",
            )
        }

    @Test
    fun oneSectionNeedsNoHeaderAndTheFlagKeepsOneList() =
        runTest {
            put("a")
            put("b")
            assertEquals(
                listOf("a", "b"),
                MainViewModel(core, backgroundScope, sections = true)
                    .also {
                        testScheduler.runCurrent()
                    }.rows(),
            )
            put("s", TaskPlace.Settled)
            put("c")
            assertEquals(
                listOf("a", "b", "s", "c"),
                MainViewModel(core, backgroundScope)
                    .also {
                        testScheduler.runCurrent()
                    }.rows(),
            )
        }

    @Test
    fun aSnoozeRunsFromNow() =
        runTest {
            put("a")
            val main = MainViewModel(core, backgroundScope, sections = true, now = { 1_000 })
            main.snooze(TaskId("a"), 60_000)
            main.snooze(TaskId("a"), null)
            main.place(TaskId("a"), TaskPlace.Pinned)
            testScheduler.runCurrent()
            assertTrue("place a Snoozed(untilMs=61000)" in core.calls, core.calls.toString())
            assertTrue("place a Snoozed(untilMs=null)" in core.calls)
            assertEquals(
                TaskPlace.Pinned,
                core.state.value[TaskId("a")]!!
                    .task.place,
            )
        }

    @Test
    fun theFlagReachesTheWindow() =
        runTest {
            val catalog = FakeCatalog(core)
            assertEquals(false, ViewModels(core, catalog, backgroundScope).main.sections)
            assertEquals(true, ViewModels(core, catalog, backgroundScope, sections = true).main.sections)
        }
}
