package octo.core.io

import kotlinx.coroutines.flow.StateFlow

/** An ordered list Octo keeps between runs; [JsonListFile] and the SQLite storage implement it. */
interface ListStore<T> {
    val items: StateFlow<List<T>>

    /** Set when the stored list could not be read; every change is then refused with it. */
    val warning: String?

    /** Applies [update] and stores the result; [update] may throw to refuse the change. */
    suspend fun change(update: (List<T>) -> List<T>)
}
