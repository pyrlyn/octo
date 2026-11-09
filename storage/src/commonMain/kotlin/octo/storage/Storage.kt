package octo.storage

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import octo.core.access.Device
import octo.core.access.Share
import octo.core.io.ListStore
import octo.core.paths.OctoPaths
import octo.core.persist.StateStore
import octo.core.preset.Preset
import octo.core.terms.AcknowledgementStore
import octo.core.triggers.TriggerRecord
import octo.core.usage.UsageStore

/**
 * Everything Octo keeps between runs except its configuration, in one SQLite database
 * ([OctoPaths.database]). Opening never fails: a database that cannot be opened is moved aside
 * and a new one started, and failing that Octo runs on one in memory; [warnings] says which.
 */
class Storage private constructor(
    internal val db: OctoDatabase,
    val warnings: List<String>,
) : AutoCloseable {
    val state: StateStore = RoomStateStore(db)

    val acknowledgements: AcknowledgementStore = RoomAcknowledgements(db)

    suspend fun devices(): ListStore<Device> =
        list("paired devices", { db.access().devices().map { it.toDevice() } }, db.access()::replaceDevices)

    suspend fun shares(): ListStore<Share> =
        list("shared links", {
            db.access().shares().map { it.toShare() }
        }, db.access()::replaceShares)

    suspend fun presets(): ListStore<Preset> =
        list("presets", {
            db.records().presets().map { it.toPreset() }
        }, db.records()::replacePresets)

    suspend fun triggerRecords(): ListStore<TriggerRecord> =
        list("trigger records", { db.records().triggers().map { it.toTrigger() } }, db.records()::replaceTriggers)

    suspend fun usage(): UsageStore {
        val (records, warning) = readOrWarn("usage records") { sqlite { db.usage().all() }.map { it.toRecord() } }
        return RoomUsageStore(db, records, warning)
    }

    override fun close() = db.close()

    private suspend fun <T> list(
        what: String,
        read: suspend () -> List<T>,
        replace: suspend (List<T>) -> Unit,
    ): ListStore<T> {
        val (items, warning) = readOrWarn(what) { sqlite { read() } }
        return RoomListStore(db, items, warning, replace)
    }

    // Unreadable rows leave the list empty and read-only, so a change never overwrites them.
    private suspend fun <T> readOrWarn(
        what: String,
        read: suspend () -> List<T>,
    ): Pair<List<T>, String?> =
        try {
            read() to null
        } catch (e: IOException) {
            emptyList<T>() to "Could not read the $what, so they are read-only: ${e.message}"
        } catch (e: IllegalArgumentException) {
            emptyList<T>() to "Could not read the $what, so they are read-only: ${e.message}"
        }

    companion object {
        /** Opens [OctoPaths.database], importing the JSON files of earlier versions the first time. */
        suspend fun open(
            paths: OctoPaths,
            fs: FileSystem = SystemFileSystem,
        ): Storage {
            val warnings = mutableListOf<String>()
            val db =
                openFile(paths.database, fs, warnings)
                    ?: build(Room.inMemoryDatabaseBuilder { OctoDatabaseConstructor.initialize() }).also {
                        warnings += "Octo is running without saving anything until ${paths.database} can be opened"
                    }
            warnings += JsonImport(db, paths, fs).run()
            return Storage(db, warnings)
        }

        /** A database that lives only as long as the returned [Storage]; for tests. */
        fun inMemory(): Storage =
            Storage(
                build(
                    Room.inMemoryDatabaseBuilder {
                        OctoDatabaseConstructor.initialize()
                    },
                ),
                emptyList(),
            )

        private suspend fun openFile(
            path: Path,
            fs: FileSystem,
            warnings: MutableList<String>,
        ): OctoDatabase? {
            withContext(Dispatchers.IO) { path.parent?.let(fs::createDirectories) }
            val (db, problem) = tryOpen(path)
            if (problem == null) return db
            val (retried, still) =
                try {
                    val aside = moveAside(path, fs)
                    warnings += "Could not open $path ($problem); it was moved to $aside and a new database started"
                    tryOpen(path)
                } catch (e: IOException) {
                    null to "$problem, and it could not be moved aside: ${e.message}"
                }
            still?.let { warnings += "Could not open $path: $it" }
            return retried
        }

        /** The open database, or the reason it could not be opened. */
        private suspend fun tryOpen(path: Path): Pair<OctoDatabase?, String?> {
            val db = build(Room.databaseBuilder(path.toString()) { OctoDatabaseConstructor.initialize() })
            val problem = probe(db)
            if (problem != null) db.close()
            return db.takeIf { problem == null } to problem
        }

        // Room opens lazily, so a first read is what finds a broken file or a schema it cannot use.
        private suspend fun probe(db: OctoDatabase): String? =
            try {
                sqlite { db.meta().get(JsonImport.IMPORTED) }
                null
            } catch (e: IOException) {
                e.message ?: "unreadable"
            } catch (e: IllegalStateException) {
                e.message ?: "unusable"
            }

        // The write-ahead log and shared memory go with the database, or SQLite would replay them.
        private suspend fun moveAside(
            path: Path,
            fs: FileSystem,
        ): Path =
            withContext(Dispatchers.IO) {
                for (suffix in listOf("", "-wal", "-shm")) {
                    val file = Path("$path$suffix")
                    if (fs.exists(file)) fs.atomicMove(file, Path("$path$suffix.corrupt"))
                }
                Path("$path.corrupt")
            }

        private fun build(builder: RoomDatabase.Builder<OctoDatabase>): OctoDatabase =
            builder
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
    }
}
