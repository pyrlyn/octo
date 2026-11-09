package octo.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import octo.core.io.ListStore
import octo.core.io.ReadOnlyFileException

/** A [ListStore] kept in one table, replaced as a whole in one transaction on every change. */
internal class RoomListStore<T>(
    private val db: OctoDatabase,
    initial: List<T>,
    /** Set when the table could not be read; every change is then refused with it. */
    override val warning: String?,
    private val replace: suspend (List<T>) -> Unit,
) : ListStore<T> {
    private val lock = Mutex()
    private val mutable = MutableStateFlow(initial)
    override val items: StateFlow<List<T>> = mutable.asStateFlow()

    override suspend fun change(update: (List<T>) -> List<T>) =
        lock.withLock {
            warning?.let { throw ReadOnlyFileException(it) }
            val next = update(mutable.value)
            db.write { replace(next) }
            mutable.value = next
        }
}
