package octo.testing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import octo.core.agent.AgentEvent
import octo.core.agent.AgentSession

/** Collects every event of [session] so a test can wait for one and still check the whole stream. */
class Events(
    scope: CoroutineScope,
    session: AgentSession,
) {
    @PublishedApi
    internal val channel = Channel<AgentEvent>(Channel.UNLIMITED)
    val seen = mutableListOf<AgentEvent>()

    init {
        scope.launch {
            session.events.collect { channel.send(it) }
            channel.close()
        }
    }

    suspend inline fun <reified T : AgentEvent> next(): T {
        while (true) {
            val event = channel.receive()
            seen += event
            if (event is T) return event
        }
    }

    /** Waits for the session to end and returns everything it emitted. */
    suspend fun drain(): List<AgentEvent> {
        for (event in channel) seen += event
        return seen
    }
}
