package octo.storage

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteException
import kotlinx.io.IOException
import kotlinx.serialization.json.Json

internal val json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

/**
 * Runs [block] in one write transaction. A SQLite failure becomes an [IOException], the failure the
 * core already treats as "could not save" and fails open on.
 */
internal suspend fun <R> OctoDatabase.write(block: suspend () -> R): R =
    sqlite { useWriterConnection { it.immediateTransaction { block() } } }

internal suspend fun <R> sqlite(block: suspend () -> R): R =
    try {
        block()
    } catch (e: SQLiteException) {
        throw IOException(e.message, e)
    }
