package octo.core.compare

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import octo.core.agent.AgentId
import octo.core.agent.MODEL_OPTION
import octo.core.orchestrator.setup
import octo.core.task.NewTask
import octo.core.task.TaskGroup
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class CompareTest {
    private val request =
        NewTask(
            repo = "/src/app",
            baseRef = "main",
            agent = AgentId("unused"),
            title = "Fix login",
            prompt = "Fix the login bug",
            options = mapOf(MODEL_OPTION to "stale", "effort" to "high"),
        )

    @Test
    fun eachPickGetsItsOwnTaskInOneGroup() =
        runTest {
            var next = 0
            val s = setup(newId = { "id${++next}" })
            val result =
                s.core.compare(
                    request,
                    listOf(AgentPick(AgentId("fake")), AgentPick(AgentId("fake"), "large"), AgentPick(AgentId("fake"))),
                    groupId = "g1",
                )
            testScheduler.runCurrent()

            assertEquals(emptyList(), result.failures)
            val tasks =
                result.tasks.map {
                    s.core.state.value[it]!!
                        .task
                }
            assertEquals(
                listOf("Fix login · fake", "Fix login · fake (large)"),
                tasks.map { it.title },
                "a repeat is dropped",
            )
            assertEquals(listOf(TaskGroup("g1", "Fix login")), tasks.map { it.group }.distinct())
            assertEquals(2, tasks.map { it.branch }.toSet().size)
            assertEquals(
                listOf(
                    mapOf("effort" to "high"),
                    mapOf("effort" to "high", MODEL_OPTION to "large"),
                ),
                tasks.map {
                    it.options
                },
            )
            assertEquals(
                listOf("Fix the login bug", "Fix the login bug"),
                s.adapter.sessions.map { it.prompts.single() },
            )
        }

    @Test
    fun aPickThatFailsLeavesTheOthersRunning() =
        runTest {
            var next = 0
            val s = setup(newId = { "id${++next}" })
            val result = s.core.compare(request, listOf(AgentPick(AgentId("ghost")), AgentPick(AgentId("fake"))))
            testScheduler.runCurrent()

            assertEquals(1, result.tasks.size)
            assertEquals(listOf(AgentPick(AgentId("ghost"))), result.failures.map { it.first })
            assertEquals(1, s.adapter.sessions.size)
        }
}
