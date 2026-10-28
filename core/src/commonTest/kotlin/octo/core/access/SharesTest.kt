package octo.core.access

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import octo.core.agent.AgentEvent
import octo.core.agent.AgentId
import octo.core.agent.StopReason
import octo.core.io.AtomicFile
import octo.core.orchestrator.Setup
import octo.core.orchestrator.setup
import octo.core.task.NewTask
import octo.core.task.TaskId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SharesTest {
    private suspend fun TestScope.shares(): Pair<Setup, Shares> {
        val s = setup()
        return s to Shares.open(AtomicFile(Path(s.home, "shares.json")), FakeSecrets(), s.core)
    }

    private suspend fun TestScope.task(s: Setup): TaskId {
        val id = s.core.createTask(NewTask("/src/app", "main", AgentId("fake"), "Fix it", prompt = "Go"))
        testScheduler.runCurrent()
        s.adapter.sessions
            .single()
            .emit(AgentEvent.TurnEnded(StopReason.EndTurn))
        testScheduler.runCurrent()
        return id
    }

    @Test
    fun aLinkOpensOneTaskUntilANewOneOrARevokeEndsIt() =
        runTest {
            val (s, shares) = shares()
            val id = TaskId("t1")
            val token = shares.share(id)
            assertEquals(id, assertNotNull(shares.share(token)).taskId)
            assertTrue(shares.shared(id))
            assertNull(shares.share("guess"))
            assertNull(shares.share(null))
            assertFalse(
                token in AtomicFile(Path(s.home, "shares.json")).read().orEmpty(),
                "the file must not hold the token",
            )

            val again = shares.share(id)
            assertNotEquals(token, again)
            assertNull(shares.share(token), "a new link ends the old one")
            assertEquals(1, shares.shares.value.size)

            shares.revoke(id)
            assertNull(shares.share(again))
            assertFalse(shares.shared(id))
        }

    @Test
    fun aGuestsMessageReachesTheAgentOnlyOnceTheOwnerApprovesIt() =
        runTest {
            val (s, shares) = shares()
            val id = task(s)
            val share = assertNotNull(shares.share(shares.share(id)))
            val proposal = shares.propose(share, "  Ann ", "Try the other fix")
            assertEquals("Ann", proposal.from)
            assertEquals(
                listOf("Go"),
                s.adapter.sessions
                    .single()
                    .prompts,
                "nothing is sent while it waits",
            )

            shares.approve(proposal.id)
            testScheduler.runCurrent()
            assertEquals(
                listOf("Go", "Try the other fix"),
                s.adapter.sessions
                    .single()
                    .prompts,
            )
            assertEquals(
                ProposalStatus.Approved,
                shares.proposals.value
                    .single()
                    .status,
            )
            assertFailsWith<IllegalStateException> { shares.approve(proposal.id) }

            val other = shares.propose(share, "", "No")
            assertEquals("Guest", other.from)
            shares.reject(other.id)
            assertEquals(
                ProposalStatus.Rejected,
                shares.proposals.value
                    .last()
                    .status,
            )
            assertEquals(
                2,
                s.adapter.sessions
                    .single()
                    .prompts.size,
            )
        }

    @Test
    fun aFailedSendLeavesTheMessageWaiting() =
        runTest {
            val (_, shares) = shares()
            val share = assertNotNull(shares.share(shares.share(TaskId("gone"))))
            val proposal = shares.propose(share, "Ann", "Hello")
            assertFails { shares.approve(proposal.id) }
            assertEquals(
                ProposalStatus.Pending,
                shares.proposals.value
                    .single()
                    .status,
            )
        }

    @Test
    fun aGuestCannotFloodTheOwner() =
        runTest {
            val (_, shares) = shares()
            val id = TaskId("t1")
            val share = assertNotNull(shares.share(shares.share(id)))
            assertFailsWith<IllegalArgumentException> { shares.propose(share, "Ann", " ") }
            assertFailsWith<IllegalArgumentException> { shares.propose(share, "Ann", "x".repeat(Shares.MAX_TEXT + 1)) }
            assertFailsWith<IllegalArgumentException> { shares.propose(share, "A\u0007nn", "Hi") }
            assertFailsWith<IllegalArgumentException> { shares.propose(share, "a".repeat(Devices.MAX_NAME + 1), "Hi") }

            repeat(Shares.MAX_PENDING) { shares.propose(share, "Ann", "Hi $it") }
            assertFailsWith<IllegalStateException> { shares.propose(share, "Ann", "One more") }

            repeat(Shares.KEPT) {
                shares.reject(
                    shares.proposals.value
                        .first { it.status == ProposalStatus.Pending }
                        .id,
                )
                shares.propose(share, "Ann", "Again $it")
            }
            assertTrue(shares.proposals.value.size <= Shares.KEPT, "settled messages are dropped past the cap")

            shares.revoke(id)
            assertTrue(shares.proposals.value.isEmpty(), "revoking drops what the guest proposed")
        }
}
