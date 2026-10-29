package octo.feature.task

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import octo.core.access.JvmSecrets
import octo.core.access.Shares
import octo.core.io.AtomicFile
import octo.core.task.TaskId
import octo.testing.FakeOctoCore
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ShareViewModelTest {
    private val file = Path(Files.createTempDirectory("octo-ui-shares").toString(), "shares.json")
    private val core = FakeOctoCore()

    @Test
    fun theLinkIsShownOnceAndTheOwnerDecidesWhatIsSent() =
        runTest {
            val shares = Shares.open(AtomicFile(file), JvmSecrets, core)
            val vm = ShareViewModel(shares, TaskId("a"), "https://box", backgroundScope)
            vm.open()
            vm.create()
            // The file is written off the test dispatcher, so wait for the state instead of the scheduler.
            val link = assertNotNull(vm.state.first { it.link != null }.link)
            assertTrue(link.startsWith("https://box/share#"), link)
            assertTrue(vm.state.first { it.shared }.shared)
            val share = assertNotNull(shares.share(link.substringAfter('#')))

            vm.dismiss()
            assertNull(vm.state.value.link, "a closed dialog forgets the link")
            shares.propose(share, "Ann", "Try again")
            shares.propose(share, "Ann", "Or not")
            runCurrent()
            val (first, second) = vm.state.value.waiting
            vm.approve(first.id)
            vm.reject(second.id)
            vm.state.first { it.waiting.isEmpty() }
            assertTrue("prompt Try again" in core.calls)
            assertTrue(core.calls.none { it == "prompt Or not" })

            vm.revoke()
            vm.state.first { !it.shared }
            assertNull(shares.share(link.substringAfter('#')))
        }

    @Test
    fun aFailedSendShowsWhy() =
        runTest {
            val failing =
                object : octo.core.orchestrator.OctoCore by core {
                    override suspend fun sendPrompt(
                        taskId: TaskId,
                        text: String,
                        attachments: List<octo.core.task.NewAttachment>,
                    ): Unit = error("agent went away")
                }
            val shares = Shares.open(AtomicFile(file), JvmSecrets, failing)
            val vm = ShareViewModel(shares, TaskId("a"), "https://box", backgroundScope)
            val share = assertNotNull(shares.share(shares.share(TaskId("a"))))
            shares.propose(share, "Ann", "Hi")
            runCurrent()
            vm.approve(
                vm.state.value.waiting
                    .single()
                    .id,
            )
            assertEquals("agent went away", vm.state.first { it.error != null }.error)
            assertEquals(1, vm.state.value.waiting.size, "the message still waits")
        }
}
