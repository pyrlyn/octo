package octo.core.preset

import kotlinx.coroutines.flow.StateFlow
import octo.core.io.AtomicFile
import octo.core.io.JsonListFile

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

/** Presets in `presets.json` in the Octo home; see [JsonListFile] for how a broken file is kept. */
class FilePresetLibrary private constructor(
    private val file: JsonListFile<Preset>,
) : PresetLibrary {
    override val presets: StateFlow<List<Preset>> = file.items

    /** Set when the file could not be read; every change is refused until it is fixed. */
    val warning: String? get() = file.warning

    override suspend fun put(
        preset: Preset,
        replacing: String?,
    ) {
        preset.problem()?.let { throw PresetException(it) }
        val clean = preset.copy(name = preset.name.trim())
        file.change { all ->
            val old = replacing ?: clean.name
            if (clean.name != old && all.any { it.name == clean.name }) {
                throw PresetException("A preset named \"${clean.name}\" already exists")
            }
            val at = all.indexOfFirst { it.name == old }
            if (at < 0) all + clean else all.toMutableList().apply { set(at, clean) }
        }
    }

    override suspend fun remove(name: String) = file.change { all -> all.filter { it.name != name } }

    companion object {
        suspend fun open(file: AtomicFile) = FilePresetLibrary(JsonListFile.open(file, Preset.serializer(), "presets"))
    }
}
