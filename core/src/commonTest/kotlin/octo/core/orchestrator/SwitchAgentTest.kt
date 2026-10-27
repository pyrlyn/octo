package octo.core.orchestrator

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import octo.core.agent.AgentEvent
import octo.core.agent.AgentId
import octo.core.agent.Capability
import octo.core.agent.MODEL_OPTION
import octo.core.agent.Role
import octo.core.agent.StopReason
import octo.core.status.AgentStatus
import octo.core.task.NewTask
import octo.core.task.TaskId
import octo.core.task.TranscriptEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SwitchAgentTest {
    private val newTask =
        NewTask(
            repo = "/src/app",
            baseRef = "main",
            agent = AgentId("fake"),
            title = "Fix it",
            prompt = "Fix the login bug",
            options = mapOf(MODEL_OPTION to "small", "effort" to "high"),
        )

    private val models = setOf(Capability.Resume, Capability.ModelSelection)

    private suspend fun TestScope.started(s: Setup): TaskId {
        val id = s.core.createTask(newTask)
        testScheduler.runCurrent()
        s.adapter.sessions
            .single()
            .emit(
                AgentEvent.Message(Role.Assistant, "The token check is wrong."),
                AgentEvent.TurnEnded(StopReason.EndTurn),
            )
        testScheduler.runCurrent()
        return id
    }

    private suspend fun Setup.notices(id: TaskId) =
        core
            .transcript(id)
            .first()
            .filterIsInstance<TranscriptEntry.Notice>()
            .map { it.text }

    @Test
    fun theSameAgentSwitchesItsModelInPlace() =
        runTest {
            val s = setup(FakeAdapter(models))
            val id = started(s)
            s.core.switchAgent(id, AgentId("fake"), "large")
            testScheduler.runCurrent()

            val session = s.adapter.sessions.single()
            assertEquals(listOf("large"), session.models)
            val task =
                s.core.state.value[id]!!
                    .task
            assertEquals(mapOf(MODEL_OPTION to "large", "effort" to "high"), task.options)
            assertNull(task.handoffFrom)
            assertTrue("Model: large" in s.notices(id))

            s.core.sendPrompt(id, "Now the tests")
            assertEquals("Now the tests", session.prompts.last(), "the session still has its own context")
        }

    @Test
    fun anotherAgentStartsFreshAndTheNextPromptCarriesTheConversation() =
        runTest {
            val other = FakeAdapter(id = AgentId("other"), displayName = "Other")
            val s = setup(FakeAdapter(models), others = listOf(other))
            val id = started(s)
            s.core.switchAgent(id, AgentId("other"), null)
            testScheduler.runCurrent()

            assertEquals(1, s.adapter.sessions.size)
            val spec = other.specs.single()
            assertNull(spec.resume)
            assertEquals(mapOf("effort" to "high"), spec.options, "the old agent's model never carries over")
            val task =
                s.core.state.value[id]!!
                    .task
            assertEquals(AgentId("other"), task.agent)
            assertEquals("Fake", task.handoffFrom)
            assertEquals(
                AgentStatus.Idle,
                s.core.state.value[id]!!
                    .status,
            )

            s.core.sendPrompt(id, "Now the tests")
            val session = other.sessions.single()
            val handoff = session.prompts.single()
            assertTrue("Fix the login bug" in handoff)
            assertTrue("The token check is wrong." in handoff)
            assertTrue(handoff.endsWith("Now the tests"))
            assertNull(
                s.core.state.value[id]!!
                    .task.handoffFrom,
            )
            assertTrue("Handed the conversation over from Fake" in s.notices(id))

            s.core.sendPrompt(id, "Thanks")
            assertEquals("Thanks", session.prompts.last(), "the handoff goes out once")
        }

    @Test
    fun aRefusedModelSwitchFallsBackToAFreshSession() =
        runTest {
            val s = setup(FakeAdapter(models, refuseModel = true))
            val id = started(s)
            s.core.switchAgent(id, AgentId("fake"), "large")
            testScheduler.runCurrent()

            assertEquals(2, s.adapter.sessions.size)
            val spec = s.adapter.specs.last()
            assertNull(spec.resume)
            assertEquals("large", spec.options[MODEL_OPTION])
            assertEquals(
                "Fake",
                s.core.state.value[id]!!
                    .task.handoffFrom,
            )
        }

    @Test
    fun aModelSwitchWithoutTheCapabilityStartsFresh() =
        runTest {
            val s = setup()
            val id = started(s)
            s.core.switchAgent(id, AgentId("fake"), "large")
            testScheduler.runCurrent()
            assertEquals(2, s.adapter.sessions.size)
            assertTrue(
                s.adapter.sessions[0]
                    .models
                    .isEmpty(),
            )
        }

    @Test
    fun anUnknownAgentChangesNothing() =
        runTest {
            val s = setup()
            val id = started(s)
            assertFailsWith<UnknownAgentException> { s.core.switchAgent(id, AgentId("ghost"), null) }
            assertEquals(
                AgentId("fake"),
                s.core.state.value[id]!!
                    .task.agent,
            )
            assertEquals(
                AgentStatus.Idle,
                s.core.state.value[id]!!
                    .status,
            )
        }
}
