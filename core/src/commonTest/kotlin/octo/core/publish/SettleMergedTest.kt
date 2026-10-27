package octo.core.publish

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import octo.core.agent.AgentId
import octo.core.orchestrator.setup
import octo.core.task.NewTask
import octo.core.task.TaskId
import octo.core.task.TaskPlace
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SettleMergedTest {
    private fun request(
        repo: String,
        title: String,
    ) = NewTask(repo = repo, baseRef = "main", agent = AgentId("fake"), title = title)

    @Test
    fun aMergedBranchSettlesItsTaskOnce() =
        runTest {
            var next = 0
            val s = setup(newId = { "id${++next}" })
            val merged = s.core.createTask(request("/src/app", "Merged"))
            val open = s.core.createTask(request("/src/app", "Open"))
            s.workspace.root = "/src/other"
            val elsewhere = s.core.createTask(request("/src/other", "Elsewhere"))
            testScheduler.runCurrent()
            val tasks =
                s.core.state.value.tasks
                    .associate { it.task.id to it.task }
            val asked = mutableListOf<String>()
            val settle =
                SettleMerged(s.core) { repo ->
                    asked += repo
                    if (repo == tasks.getValue(elsewhere).repo) error("not a GitHub repository")
                    setOf(tasks.getValue(merged).branch, "someone-else")
                }

            settle.check()

            fun place(id: TaskId) =
                s.core.state.value[id]!!
                    .task.place
            assertEquals(TaskPlace.Settled, place(merged))
            assertEquals(TaskPlace.Active, place(open))
            assertEquals(TaskPlace.Active, place(elsewhere), "a repository gh cannot answer is skipped")
            assertEquals(2, asked.size, "one question per repository")

            s.core.setPlace(merged, TaskPlace.Active)
            settle.check()
            assertEquals(TaskPlace.Active, place(merged), "moved back by the user, it stays")
        }
}
