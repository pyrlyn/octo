package octo.adapters.base

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import octo.core.agent.AgentEvent
import octo.core.agent.RequestResponse

/**
 * Agent requests waiting on the user (permissions, questions). Every request ends with exactly
 * one [AgentEvent.RequestResolved], whoever ends it, so the status never stays blocked.
 */
class UserRequests(
    private val emit: (AgentEvent) -> Unit,
) {
    private val lock = Mutex()
    private val waiting = mutableMapOf<String, CompletableDeferred<RequestResponse>>()

    /** Emits [event] and suspends until the user answers or the request is withdrawn. */
    suspend fun ask(
        id: String,
        event: AgentEvent,
    ): RequestResponse {
        val reply = CompletableDeferred<RequestResponse>()
        lock.withLock { waiting[id] = reply }
        emit(event)
        try {
            return reply.await()
        } finally {
            withContext(NonCancellable) { finish(id, RequestResponse.Cancelled) }
        }
    }

    /** False when nothing with [id] is waiting, for example because the agent already withdrew it. */
    suspend fun respond(
        id: String,
        response: RequestResponse,
    ): Boolean = finish(id, response)

    suspend fun withdraw(id: String) {
        finish(id, RequestResponse.Cancelled)
    }

    suspend fun withdrawAll() {
        lock.withLock { waiting.keys.toList() }.forEach { withdraw(it) }
    }

    private suspend fun finish(
        id: String,
        response: RequestResponse,
    ): Boolean {
        val reply = lock.withLock { waiting.remove(id) } ?: return false
        reply.complete(response)
        emit(AgentEvent.RequestResolved(id))
        return true
    }
}
