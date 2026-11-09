package octo.storage

import kotlinx.io.IOException
import octo.core.terms.AcknowledgementStore

internal class RoomAcknowledgements(
    private val db: OctoDatabase,
) : AcknowledgementStore {
    // Unreadable acknowledgements read as none: the notices only show again.
    @Suppress("SwallowedException")
    override suspend fun read(): Set<String> =
        try {
            sqlite { db.records().acknowledgements() }.mapTo(mutableSetOf()) { it.key }
        } catch (e: IOException) {
            emptySet()
        }

    // Acknowledgements only grow, so the keys already stored are left as they are.
    override suspend fun write(keys: Set<String>) =
        db.write { db.records().insertAcknowledgements(keys.map(::AcknowledgementRow)) }
}
