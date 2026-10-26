package octo.core.preset

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import octo.core.io.AtomicFile

/** The saved presets; the UI's only way to read or change them. */
interface PresetLibrary {
    val presets: StateFlow<List<Preset>>

    /** Saves [preset], replacing the one named [replacing] (or the same name) if there is one. */
    suspend fun put(
        preset: Preset,
        replacing: String? = null,
    )

    suspend fun remove(name: String)
}

class PresetException(
    message: String,
) : Exception(message)

/**
 * Presets in `presets.json` in the Octo home. A file that cannot be read is left as it is: the
 * library opens empty and refuses to write, so a typo in a hand-edited file never costs the user
 * their presets.
 */
class FilePresetLibrary private constructor(
    private val file: AtomicFile,
    initial: List<Preset>,
    /** Set when the file could not be read; every change is refused with it. */
    val warning: String?,
) : PresetLibrary {
    private val lock = Mutex()
    private val mutable = MutableStateFlow(initial)
    override val presets: StateFlow<List<Preset>> = mutable.asStateFlow()

    override suspend fun put(
        preset: Preset,
        replacing: String?,
    ) {
        preset.problem()?.let { throw PresetException(it) }
        val clean = preset.copy(name = preset.name.trim())
        change { all ->
            val old = replacing ?: clean.name
            if (clean.name != old && all.any { it.name == clean.name }) {
                throw PresetException("A preset named \"${clean.name}\" already exists")
            }
            val at = all.indexOfFirst { it.name == old }
            if (at < 0) all + clean else all.toMutableList().apply { set(at, clean) }
        }
    }

    override suspend fun remove(name: String) = change { all -> all.filter { it.name != name } }

    private suspend fun change(update: (List<Preset>) -> List<Preset>) =
        lock.withLock {
            warning?.let { throw PresetException(it) }
            val next = update(mutable.value)
            file.write(json.encodeToString(PresetFile.serializer(), PresetFile(VERSION, next)))
            mutable.value = next
        }

    @Serializable
    private data class PresetFile(
        val version: Int,
        val presets: List<Preset>,
    )

    companion object {
        const val VERSION = 1

        private val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                prettyPrint = true
            }

        // SerializationException is an IllegalArgumentException, and so is a broken value inside it.
        suspend fun open(file: AtomicFile): FilePresetLibrary =
            try {
                val presets = file.read()?.let { json.decodeFromString(PresetFile.serializer(), it).presets }
                FilePresetLibrary(file, presets.orEmpty(), null)
            } catch (e: IOException) {
                broken(file, e.message)
            } catch (e: IllegalArgumentException) {
                broken(file, e.message)
            }

        private fun broken(
            file: AtomicFile,
            reason: String?,
        ) = FilePresetLibrary(
            file,
            emptyList(),
            "Could not read ${file.path}, so presets are read-only until it is fixed or removed: $reason",
        )
    }
}
