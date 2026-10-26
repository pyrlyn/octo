package octo.core.io

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.IOException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** A change was refused because the file could not be read when it was opened. */
class ReadOnlyFileException(
    message: String,
) : IllegalStateException(message)

/**
 * A list kept as `{"version": 1, "<key>": [...]}` in an [AtomicFile] the user may also edit by
 * hand. A file that cannot be read is left as it is: the list opens empty and refuses every
 * change, so a typo never costs the user what the file holds.
 */
class JsonListFile<T> private constructor(
    private val file: AtomicFile,
    private val serializer: KSerializer<T>,
    private val key: String,
    initial: List<T>,
    /** Set when the file could not be read; every change is refused with it. */
    val warning: String?,
) {
    private val lock = Mutex()
    private val mutable = MutableStateFlow(initial)
    val items: StateFlow<List<T>> = mutable.asStateFlow()

    /** Applies [update] and writes the result; [update] may throw to refuse the change. */
    suspend fun change(update: (List<T>) -> List<T>) =
        lock.withLock {
            warning?.let { throw ReadOnlyFileException(it) }
            val next = update(mutable.value)
            val text =
                buildJsonObject {
                    put("version", VERSION)
                    put(key, json.encodeToJsonElement(ListSerializer(serializer), next))
                }
            file.write(json.encodeToString(JsonObject.serializer(), text))
            mutable.value = next
        }

    companion object {
        const val VERSION = 1

        private val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                prettyPrint = true
            }

        // SerializationException is an IllegalArgumentException, and so is a broken value inside it.
        suspend fun <T> open(
            file: AtomicFile,
            serializer: KSerializer<T>,
            key: String,
        ): JsonListFile<T> =
            try {
                val items = file.read()?.let { decode(it, serializer, key) }
                JsonListFile(file, serializer, key, items.orEmpty(), null)
            } catch (e: IOException) {
                broken(file, serializer, key, e.message)
            } catch (e: IllegalArgumentException) {
                broken(file, serializer, key, e.message)
            }

        private fun <T> decode(
            text: String,
            serializer: KSerializer<T>,
            key: String,
        ): List<T> {
            val root = json.parseToJsonElement(text).jsonObject
            // A newer Octo may have written fields this one would drop on the next save.
            val version = (root["version"] as? JsonPrimitive)?.intOrNull
            require(version == VERSION) { "unsupported version $version" }
            val items = requireNotNull(root[key]) { "no \"$key\" list" }
            return json.decodeFromJsonElement(ListSerializer(serializer), items)
        }

        private fun <T> broken(
            file: AtomicFile,
            serializer: KSerializer<T>,
            key: String,
            reason: String?,
        ) = JsonListFile(
            file,
            serializer,
            key,
            emptyList(),
            "Could not read ${file.path}, so it is read-only until it is fixed or removed: $reason",
        )
    }
}
