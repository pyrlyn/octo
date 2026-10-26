package octo.adapters.base

import kotlinx.coroutines.flow.Flow
import octo.core.agent.AgentEvent
import octo.core.agent.LogStream
import octo.core.agent.RequestResponse
import octo.core.process.RunningProcess
import kotlin.coroutines.CoroutineContext

/** A session whose agent speaks JSON-RPC over stdio (ACP, Codex `app-server`). */
abstract class JsonRpcAgentSession(
    process: RunningProcess,
    context: CoroutineContext,
    withVersionField: Boolean = true,
) : ProcessAgentSession(process, context),
    JsonRpcHandler {
    protected val peer =
        JsonRpcPeer(scope, process::writeLine, this, withVersionField) { emit(AgentEvent.Log(LogStream.Protocol, it)) }
    protected val requests = UserRequests(::emit)

    override suspend fun readOutput(lines: Flow<String>) {
        try {
            peer.run(lines)
        } finally {
            requests.withdrawAll()
        }
    }

    override suspend fun respond(
        requestId: String,
        response: RequestResponse,
    ) {
        requests.respond(requestId, response)
    }
}
