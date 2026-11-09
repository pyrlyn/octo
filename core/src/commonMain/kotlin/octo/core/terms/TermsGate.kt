package octo.core.terms

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import octo.core.agent.AgentAdapter
import octo.core.agent.AgentId
import octo.core.agent.VendorTerms
import octo.core.io.AtomicFile

/** Where the acknowledged terms are kept; what cannot be read reads as nothing acknowledged. */
interface AcknowledgementStore {
    suspend fun read(): Set<String>

    suspend fun write(keys: Set<String>)
}

/**
 * Remembers which vendor terms the user has acknowledged, so each notice is shown once per terms
 * version. Lost or unreadable acknowledgements only mean the notices show again.
 */
class TermsGate(
    private val store: AcknowledgementStore,
) {
    /** Acknowledgements kept in `acknowledgements.json`, the format Octo used before SQLite. */
    constructor(file: AtomicFile) : this(JsonAcknowledgements(file))

    private val lock = Mutex()

    /** The terms the user still has to see before [adapter] may start, or null. */
    suspend fun pending(adapter: AgentAdapter): VendorTerms? {
        val terms = adapter.terms ?: return null
        return terms.takeUnless { key(adapter.id, it) in lock.withLock { store.read() } }
    }

    suspend fun acknowledge(adapter: AgentAdapter) {
        val terms = adapter.terms ?: return
        lock.withLock { store.write(store.read() + key(adapter.id, terms)) }
    }

    suspend fun requireAcknowledged(adapter: AgentAdapter) {
        pending(adapter)?.let { throw TermsNotAcknowledgedException(adapter.id, it) }
    }

    private companion object {
        fun key(
            id: AgentId,
            terms: VendorTerms,
        ) = "${id.value}@${terms.version}"
    }
}

/** `acknowledgements.json`, the format Octo used before SQLite. */
class JsonAcknowledgements(
    private val file: AtomicFile,
) : AcknowledgementStore {
    override suspend fun write(keys: Set<String>) =
        file.write(json.encodeToString(Stored.serializer(), Stored(keys.sorted())))

    override suspend fun read(): Set<String> {
        val text = file.read() ?: return emptySet()
        return try {
            json.decodeFromString(Stored.serializer(), text).acknowledged.toSet()
        } catch (_: SerializationException) {
            emptySet()
        } catch (_: IllegalArgumentException) {
            emptySet()
        }
    }

    @Serializable
    private data class Stored(
        val acknowledged: List<String> = emptyList(),
    )

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

class TermsNotAcknowledgedException(
    id: AgentId,
    val terms: VendorTerms,
) : IllegalStateException("The terms for '${id.value}' have not been acknowledged yet")
