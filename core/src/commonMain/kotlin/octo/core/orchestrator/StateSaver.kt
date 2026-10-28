package octo.core.orchestrator

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.io.IOException
import octo.core.persist.PersistedState
import octo.core.persist.StateStore
import octo.core.task.OctoState
import octo.core.task.TaskId
import octo.core.task.TranscriptEntry

/** Writes the state as it changes and once more on close; a write that fails only warns. */
internal class StateSaver(
    private val store: StateStore,
    private val warn: suspend (String) -> Unit,
) {
    private var job: Job? = null

    suspend fun load() = store.load()

    fun watch(
        scope: CoroutineScope,
        state: StateFlow<OctoState>,
        transcripts: StateFlow<Map<TaskId, List<TranscriptEntry>>>,
    ) {
        // Partial text is not persisted, so streaming alone never triggers a write; drop(1) keeps
        // the backups from rotating on every launch.
        job =
            scope.launch {
                combine(state, transcripts, ::persistedState)
                    .distinctUntilChanged()
                    .drop(1)
                    .conflate()
                    .collect { save(it) }
            }
    }

    suspend fun close(
        state: OctoState,
        transcripts: Map<TaskId, List<TranscriptEntry>>,
    ) {
        job?.cancelAndJoin()
        save(persistedState(state, transcripts))
    }

    private suspend fun save(state: PersistedState) {
        try {
            store.save(state)
        } catch (e: IOException) {
            // Fail open: the session keeps running; the UI shows why nothing is being saved.
            warn("Could not save state: ${e.message}")
        }
    }
}
