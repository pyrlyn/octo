package octo.core.plugins

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import octo.core.flags.CoreFlags
import octo.core.flags.FeatureFlags
import wasm.WasmRuntime

/** The plugins that loaded, and one warning for each file that did not. */
data class LoadedPlugins(
    val plugins: List<Plugin> = emptyList(),
    val warnings: List<String> = emptyList(),
)

/** Loads every `*.wasm` in the plugins folder; a broken one is warned about and skipped. */
object PluginLoader {
    /** About one unit per guest call or loop turn; plenty for turning a task into a prompt. */
    const val FUEL = 100_000_000L
    private const val MIB = 1 shl 20
    const val MAX_MODULE = 16 * MIB

    /** With the `plugins` flag off this reads nothing at all, not even [dir]. */
    suspend fun load(
        flags: FeatureFlags,
        dir: Path,
        runtime: WasmRuntime,
        fuel: Long = FUEL,
        fs: FileSystem = SystemFileSystem,
    ): LoadedPlugins {
        if (!flags.isEnabled(CoreFlags.PLUGINS)) return LoadedPlugins()
        return withContext(Dispatchers.IO) {
            val files =
                try {
                    if (fs.exists(
                            dir,
                        )
                    ) {
                        fs.list(dir).filter { it.name.endsWith(".wasm") }.sortedBy { it.name }
                    } else {
                        emptyList()
                    }
                } catch (e: IOException) {
                    return@withContext LoadedPlugins(
                        warnings = listOf("Could not read the plugins folder $dir: ${e.message}"),
                    )
                }
            val plugins = mutableListOf<Plugin>()
            val warnings = mutableListOf<String>()
            for (file in files) {
                try {
                    val plugin = Plugin.instantiate(file.name, read(fs, file), runtime, fuel)
                    val taken = plugins.firstOrNull { it.manifest.id == plugin.manifest.id }
                    if (taken != null) throw PluginException("${taken.file} already has the id '${plugin.manifest.id}'")
                    plugins += plugin
                } catch (e: PluginException) {
                    warnings += "Skipping plugin ${file.name}: ${e.message}"
                } catch (e: IOException) {
                    warnings += "Skipping plugin ${file.name}: ${e.message}"
                }
            }
            LoadedPlugins(plugins, warnings)
        }
    }

    private fun read(
        fs: FileSystem,
        file: Path,
    ): ByteArray {
        val size = fs.metadataOrNull(file)?.size ?: 0
        if (size > MAX_MODULE) throw PluginException("it is over ${MAX_MODULE / MIB} MiB")
        return fs.source(file).buffered().use { it.readByteArray() }
    }
}
