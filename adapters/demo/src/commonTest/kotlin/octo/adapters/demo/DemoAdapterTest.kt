package octo.adapters.demo

import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.readString
import octo.core.agent.AgentEvent
import octo.core.agent.AgentSessionRef
import octo.core.agent.RequestResponse
import octo.core.agent.Role
import octo.core.agent.SessionSpec
import octo.core.agent.StopReason
import octo.testing.Events
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DemoAdapterTest {
    private fun worktree() =
        Path(SystemTemporaryDirectory, "octo-demo-${Random.nextLong().toULong()}").also {
            SystemFileSystem.createDirectories(it)
        }

    @Test
    fun allowedTurnWritesTheFile() =
        runTest {
            val dir = worktree()
            val session = DemoAdapter().start(SessionSpec(dir.toString(), resume = AgentSessionRef("old")))
            val events = Events(backgroundScope, session)
            assertEquals(AgentSessionRef("old"), events.next<AgentEvent.SessionReady>().ref)

            session.prompt("Say hi")
            val request = events.next<AgentEvent.PermissionRequested>()
            session.respond(request.id, RequestResponse.Option("allow"))
            assertEquals(
                AgentEvent.Message(Role.Assistant, "Added your prompt to octo-demo.md."),
                events.next<AgentEvent.Message>(),
            )
            assertEquals(StopReason.EndTurn, events.next<AgentEvent.TurnEnded>().stopReason)

            val file = Path(dir, DemoAdapter.FILE_NAME)
            assertEquals("- Say hi\n", SystemFileSystem.source(file).buffered().use { it.readString() })
            session.close()
            assertEquals(AgentEvent.Exited(0), events.drain().last())
        }

    @Test
    fun rejectAndCancelLeaveTheWorktreeAlone() =
        runTest {
            val dir = worktree()
            val session = DemoAdapter().start(SessionSpec(dir.toString()))
            val events = Events(backgroundScope, session)

            session.prompt("one")
            session.respond(events.next<AgentEvent.PermissionRequested>().id, RequestResponse.Option("reject"))
            assertEquals(StopReason.EndTurn, events.next<AgentEvent.TurnEnded>().stopReason)

            session.prompt("two")
            events.next<AgentEvent.PermissionRequested>()
            session.cancel()
            assertEquals(StopReason.Cancelled, events.next<AgentEvent.TurnEnded>().stopReason)
            assertFalse(SystemFileSystem.exists(Path(dir, DemoAdapter.FILE_NAME)))
            session.close()
        }
}
