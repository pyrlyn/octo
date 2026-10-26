package octo.core.persist

import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import octo.core.agent.AgentId
import octo.core.agent.AgentSessionRef
import octo.core.agent.Role
import octo.core.io.AtomicFile
import octo.core.status.AgentStatus
import octo.core.task.Layout
import octo.core.task.Task
import octo.core.task.TaskId
import octo.core.task.TranscriptEntry
import octo.core.tempDir
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StateStoreTest {
    private fun state(title: String) =
        PersistedState(
            tasks =
                listOf(
                    PersistedTask(
                        Task(
                            id = TaskId("t1"),
                            title = title,
                            repo = "/repo",
                            worktree = "/wt",
                            branch = "octo/x",
                            baseRef = "main",
                            agent = AgentId("claude"),
                            sessionRef = AgentSessionRef("s1"),
                            lastStatus = AgentStatus.Working,
                        ),
                        listOf(TranscriptEntry.Text(Role.User, "hi"), TranscriptEntry.Notice("done")),
                    ),
                ),
            layout = Layout(selectedTask = TaskId("t1"), sidebarWidth = 240f),
        )

    @Test
    fun roundTripsAndKeepsThreeBackups() =
        runTest {
            val path = Path(tempDir("octo-state"), "state.json")
            val store = StateStore(path)
            assertEquals(LoadedState(PersistedState(), emptyList()), store.load())

            (1..5).forEach { store.save(state("v$it")) }
            assertEquals(LoadedState(state("v5"), emptyList()), StateStore(path).load())
            (1..3).forEach { n -> assertTrue(SystemFileSystem.exists(Path("$path.$n"))) }
            assertTrue(!SystemFileSystem.exists(Path("$path.4")))
        }

    @Test
    fun corruptFileFallsBackToTheNewestBackup() =
        runTest {
            val path = Path(tempDir("octo-state"), "state.json")
            val store = StateStore(path)
            store.save(state("old"))
            store.save(state("new"))
            AtomicFile(path).write("{ not json")

            val loaded = store.load()
            assertEquals(state("old"), loaded.state)
            assertEquals(1, loaded.warnings.size)
            assertTrue(SystemFileSystem.exists(Path("$path.corrupt")))
        }

    @Test
    fun unknownFieldsAreIgnored() =
        runTest {
            val path = Path(tempDir("octo-state"), "state.json")
            AtomicFile(path).write("""{"version":1,"future":true,"tasks":[],"layout":{"diffOpen":true}}""")
            assertEquals(PersistedState(layout = Layout(diffOpen = true)), StateStore(path).load().state)
        }
}
