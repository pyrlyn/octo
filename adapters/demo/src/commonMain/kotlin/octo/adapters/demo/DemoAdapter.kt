package octo.adapters.demo

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import octo.core.agent.AgentAdapter
import octo.core.agent.AgentEvent
import octo.core.agent.AgentIcon
import octo.core.agent.AgentId
import octo.core.agent.AgentProbe
import octo.core.agent.AgentSession
import octo.core.agent.AgentSessionRef
import octo.core.agent.AuthState
import octo.core.agent.Capability
import octo.core.agent.PermissionKind
import octo.core.agent.PermissionOption
import octo.core.agent.PlanEntry
import octo.core.agent.RequestResponse
import octo.core.agent.Role
import octo.core.agent.SessionSpec
import octo.core.agent.StopReason
import octo.core.agent.ToolStatus
import octo.core.flags.FeatureFlag
import kotlin.random.Random

/**
 * An in-process agent for trying Octo without installing or logging in to a real one. Each
 * prompt asks for permission to write [FILE_NAME] in the worktree, so the diff view has
 * something to show.
 */
class DemoAdapter : AgentAdapter {
    override val id = ID
    override val displayName = "Demo agent"
    override val icon =
        AgentIcon(
            """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><rect width="24" height="24" rx="6" fill="#6B7280"/><circle cx="12" cy="12" r="4" fill="#fff"/></svg>""",
        )
    override val capabilities = setOf(Capability.Resume, Capability.Cancel, Capability.Permissions, Capability.Plan)
    override val featureFlag = FLAG

    override suspend fun probe() =
        AgentProbe(binaryPath = "built-in", version = VERSION, verifiedVersion = VERSION, auth = AuthState.LoggedIn)

    override suspend fun start(spec: SessionSpec): AgentSession =
        DemoSession(Path(spec.workingDir)).also {
            it.emit(AgentEvent.SessionReady(spec.resume ?: AgentSessionRef("demo-${Random.nextInt().toUInt()}")))
        }

    companion object {
        val ID = AgentId("demo")
        val FLAG = FeatureFlag("demo-agent", "A built-in demo agent that needs no install or account")
        const val FILE_NAME = "octo-demo.md"
        private const val VERSION = "1"
    }
}

private class DemoSession(
    private val worktree: Path,
) : AgentSession {
    private val channel = Channel<AgentEvent>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()
    private var turns = 0
    private var waiting: Pair<String, CompletableDeferred<RequestResponse>>? = null

    override val events: Flow<AgentEvent> = channel.receiveAsFlow()

    fun emit(event: AgentEvent) {
        channel.trySend(event)
    }

    // Returns at once like a real adapter; the turn runs until the user answers.
    override suspend fun prompt(text: String) {
        scope.launch { turn(text) }
    }

    private suspend fun turn(text: String) {
        val (request, answer) =
            lock.withLock {
                turns++
                "write-$turns" to CompletableDeferred<RequestResponse>().also { waiting = "write-$turns" to it }
            }
        val tool = "tool-$turns"
        emit(AgentEvent.TurnStarted)
        emit(AgentEvent.Thought("The user wants: $text"))
        emit(AgentEvent.PlanUpdated(listOf(PlanEntry("Write ${DemoAdapter.FILE_NAME}", done = false))))
        emit(AgentEvent.ToolCall(tool, "Write ${DemoAdapter.FILE_NAME}", "edit", ToolStatus.Pending))
        emit(AgentEvent.PermissionRequested(request, "Write ${DemoAdapter.FILE_NAME}", OPTIONS))
        val response = answer.await()
        emit(AgentEvent.RequestResolved(request))
        when {
            response == RequestResponse.Cancelled -> emit(AgentEvent.TurnEnded(StopReason.Cancelled))
            response == RequestResponse.Option(ALLOW) -> write(tool, text)
            else -> reply(tool, ToolStatus.Failed, "Skipped writing the file.")
        }
    }

    private suspend fun write(
        tool: String,
        text: String,
    ) {
        withContext(Dispatchers.IO) {
            SystemFileSystem.sink(Path(worktree, DemoAdapter.FILE_NAME), append = true).buffered().use {
                it.writeString("- $text\n")
            }
        }
        emit(AgentEvent.PlanUpdated(listOf(PlanEntry("Write ${DemoAdapter.FILE_NAME}", done = true))))
        reply(tool, ToolStatus.Completed, "Added your prompt to ${DemoAdapter.FILE_NAME}.")
    }

    private fun reply(
        tool: String,
        status: ToolStatus,
        text: String,
    ) {
        emit(AgentEvent.ToolCall(tool, "Write ${DemoAdapter.FILE_NAME}", "edit", status))
        text.split(" ").forEach { emit(AgentEvent.MessageDelta(Role.Assistant, "$it ")) }
        emit(AgentEvent.Message(Role.Assistant, text))
        emit(AgentEvent.TurnEnded(StopReason.EndTurn))
    }

    override suspend fun respond(
        requestId: String,
        response: RequestResponse,
    ) {
        lock.withLock { waiting?.takeIf { it.first == requestId }?.also { waiting = null } }?.second?.complete(response)
    }

    override suspend fun cancel() {
        lock.withLock { waiting.also { waiting = null } }?.second?.complete(RequestResponse.Cancelled)
    }

    override suspend fun close() {
        cancel()
        scope.cancel()
        emit(AgentEvent.Exited(0))
        channel.close()
    }

    private companion object {
        const val ALLOW = "allow"
        val OPTIONS =
            listOf(
                PermissionOption(ALLOW, "Allow", PermissionKind.AllowOnce),
                PermissionOption("reject", "Reject", PermissionKind.RejectOnce),
            )
    }
}
