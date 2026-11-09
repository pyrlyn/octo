package octo.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.serialization.KSerializer
import octo.core.access.Device
import octo.core.access.Devices
import octo.core.access.Share
import octo.core.access.Shares
import octo.core.io.AtomicFile
import octo.core.io.JsonListFile
import octo.core.paths.OctoPaths
import octo.core.persist.JsonStateStore
import octo.core.preset.Preset
import octo.core.preset.StoredPresetLibrary
import octo.core.terms.JsonAcknowledgements
import octo.core.triggers.TriggerRecord
import octo.core.triggers.Triggers
import octo.core.usage.UsageLog
import octo.core.usage.UsageRecord

/**
 * Moves the JSON files of the versions before SQLite into the database, once. Everything goes in
 * one transaction, marked done in the same one; each imported file is then renamed to
 * `<name>.imported`, and a file that could not be read stays where it is.
 */
internal class JsonImport(
    private val db: OctoDatabase,
    private val paths: OctoPaths,
    private val fs: FileSystem,
) {
    /** The warnings of the import; none when it already ran. */
    suspend fun run(): List<String> =
        try {
            if (sqlite { db.meta().get(IMPORTED) } != null) emptyList() else import()
        } catch (e: IOException) {
            // Left unmarked, so the next start tries again.
            listOf("Could not import the files of an earlier Octo into ${paths.database}: ${e.message}")
        }

    private suspend fun import(): List<String> {
        val warnings = mutableListOf<String>()
        val stateFiles =
            (
                listOf(
                    paths.state,
                ) + (1..JsonStateStore.BACKUPS).map { Path("${paths.state}.$it") }
            ).filter(::exists)
        val state = JsonStateStore(paths.state, fs).load().also { warnings += it.warnings }.state
        val devices = read(paths.devices, Device.serializer(), Devices.DEVICES_KEY)
        val shares = read(paths.shares, Share.serializer(), Shares.SHARES_KEY)
        val presets = read(paths.presets, Preset.serializer(), StoredPresetLibrary.PRESETS_KEY)
        val triggers = read(paths.triggers, TriggerRecord.serializer(), Triggers.TRIGGERS_KEY)
        val usage = read(paths.usage, UsageRecord.serializer(), UsageLog.RECORDS_KEY)
        val acknowledgements = JsonAcknowledgements(AtomicFile(paths.acknowledgements, fs)).read()
        val lists = listOf(devices, shares, presets, triggers, usage)
        db.write {
            RoomStateStore(db).writeChanges(state)
            db.access().replaceDevices(devices.items)
            db.access().replaceShares(shares.items)
            db.records().replacePresets(presets.items)
            db.records().replaceTriggers(triggers.items)
            db.usage().insert(usage.items.takeLast(UsageLog.MAX_RECORDS).map { it.toRow() })
            db.records().insertAcknowledgements(acknowledgements.map(::AcknowledgementRow))
            db.meta().put(MetaRow(IMPORTED, "1"))
        }
        val imported =
            stateFiles.filter(::exists) + lists.filter { it.warning == null }.map { it.path } + paths.acknowledgements
        warnings += lists.mapNotNull { list -> list.warning?.let { "Did not import ${list.path}: $it" } }
        warnings += rename(imported.filter(::exists))
        return warnings
    }

    private class Read<T>(
        val path: Path,
        val items: List<T>,
        val warning: String?,
    )

    private suspend fun <T> read(
        path: Path,
        serializer: KSerializer<T>,
        key: String,
    ): Read<T> {
        val file = JsonListFile.open(AtomicFile(path, fs), serializer, key)
        return Read(path, file.items.value, file.warning)
    }

    private suspend fun rename(files: List<Path>): List<String> =
        withContext(Dispatchers.IO) {
            files.mapNotNull { file ->
                try {
                    fs.atomicMove(file, Path("$file.imported"))
                    null
                } catch (e: IOException) {
                    "Imported $file but could not rename it: ${e.message}"
                }
            }
        }

    private fun exists(path: Path) = fs.exists(path)

    companion object {
        const val IMPORTED = "json-imported"
    }
}
