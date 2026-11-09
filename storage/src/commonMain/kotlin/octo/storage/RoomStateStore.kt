package octo.storage

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.IOException
import octo.core.persist.LoadedState
import octo.core.persist.PersistedState
import octo.core.persist.PersistedTask
import octo.core.persist.StateStore
import octo.core.task.Layout
import octo.core.task.Task
import octo.core.task.TranscriptEntry

/**
 * Tasks, transcript tails and the layout. A save writes only the tasks and transcripts that
 * changed since the last load or save, so a long transcript is not rewritten on every turn.
 */
internal class RoomStateStore(
    private val db: OctoDatabase,
) : StateStore {
    private val lock = Mutex()

    // What the database holds now, as of the last load or save.
    private var stored = PersistedState()

    // Tasks whose rows no longer decode; saves keep them, so a newer Octo can still read them.
    private var unreadable = emptySet<String>()

    override suspend fun load(): LoadedState =
        lock.withLock {
            val warnings = mutableListOf<String>()
            val skipped = mutableSetOf<String>()
            val state =
                try {
                    read(warnings, skipped)
                } catch (e: IOException) {
                    warnings += "Could not read the saved tasks: ${e.message}"
                    PersistedState()
                }
            stored = state
            unreadable = skipped
            LoadedState(state, warnings)
        }

    override suspend fun save(state: PersistedState) {
        lock.withLock {
            db.write { writeChanges(state) }
            stored = state
        }
    }

    /** Writes what changed since the last load or save; the caller holds the write transaction. */
    internal suspend fun writeChanges(state: PersistedState) {
        val before = stored.tasks.withIndex().associate { (at, it) -> it.task.id to IndexedValue(at, it) }
        run {
            val dao = db.state()
            val keep = state.tasks.map { it.task.id.value } + unreadable
            dao.deleteTasksExcept(keep)
            dao.deleteTranscriptsExcept(keep)
            val tasks =
                state.tasks.withIndex().filter { (at, it) ->
                    val old = before[it.task.id]
                    old?.index != at || old.value.task != it.task
                }
            dao.putTasks(
                tasks.map { (at, it) ->
                    TaskRow(it.task.id.value, at, json.encodeToString(Task.serializer(), it.task))
                },
            )
            for (persisted in state.tasks) {
                if (before[persisted.task.id]?.value?.transcript !=
                    persisted.transcript
                ) {
                    replaceTranscript(persisted)
                }
            }
            if (state.layout != stored.layout) {
                dao.putLayout(LayoutRow(body = json.encodeToString(Layout.serializer(), state.layout)))
            }
        }
    }

    private suspend fun replaceTranscript(persisted: PersistedTask) {
        val id = persisted.task.id.value
        db.state().deleteTranscript(id)
        db.state().insertTranscript(
            persisted.transcript.mapIndexed { seq, entry ->
                TranscriptRow(id, seq, json.encodeToString(TranscriptEntry.serializer(), entry))
            },
        )
    }

    // A row that no longer decodes is left out with a warning rather than failing the whole load.
    private suspend fun read(
        warnings: MutableList<String>,
        skipped: MutableSet<String>,
    ): PersistedState {
        val dao = db.state()
        val (tasks, transcripts, layout) = sqlite { Triple(dao.tasks(), dao.transcripts(), dao.layout()) }
        val entries = transcripts.groupBy { it.taskId }
        val persisted =
            tasks.mapNotNull { row ->
                decode(warnings, "task ${row.id}") {
                    PersistedTask(
                        json.decodeFromString(Task.serializer(), row.body),
                        entries[row.id].orEmpty().map { json.decodeFromString(TranscriptEntry.serializer(), it.body) },
                    )
                } ?: run {
                    skipped += row.id
                    null
                }
            }
        val restored =
            layout?.let {
                decode(
                    warnings,
                    "the layout",
                ) { json.decodeFromString(Layout.serializer(), it.body) }
            }
        return PersistedState(tasks = persisted, layout = restored ?: Layout())
    }

    // SerializationException is an IllegalArgumentException, and so is a broken value inside it.
    private fun <T : Any> decode(
        warnings: MutableList<String>,
        what: String,
        block: () -> T,
    ): T? =
        try {
            block()
        } catch (e: IllegalArgumentException) {
            warnings += "Ignored $what, which could not be read: ${e.message}"
            null
        }
}
