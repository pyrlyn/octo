package octo.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

// Room's annotations are its ORM layer; the @Query strings are the statements it cannot derive
// from an entity: ordered reads and bulk deletes.

@Dao
internal interface StateDao {
    @Query("SELECT * FROM tasks ORDER BY position")
    suspend fun tasks(): List<TaskRow>

    @Query("SELECT * FROM transcript_entries ORDER BY task_id, seq")
    suspend fun transcripts(): List<TranscriptRow>

    @Query("SELECT * FROM layout WHERE id = 0")
    suspend fun layout(): LayoutRow?

    @Upsert
    suspend fun putTasks(rows: List<TaskRow>)

    @Query("DELETE FROM tasks WHERE id NOT IN (:keep)")
    suspend fun deleteTasksExcept(keep: List<String>)

    @Insert
    suspend fun insertTranscript(rows: List<TranscriptRow>)

    @Query("DELETE FROM transcript_entries WHERE task_id = :taskId")
    suspend fun deleteTranscript(taskId: String)

    @Query("DELETE FROM transcript_entries WHERE task_id NOT IN (:keep)")
    suspend fun deleteTranscriptsExcept(keep: List<String>)

    @Upsert
    suspend fun putLayout(row: LayoutRow)
}

@Dao
internal interface AccessDao {
    @Query("SELECT * FROM devices ORDER BY position")
    suspend fun devices(): List<DeviceRow>

    @Query("DELETE FROM devices")
    suspend fun clearDevices()

    @Insert
    suspend fun insertDevices(rows: List<DeviceRow>)

    @Query("SELECT * FROM shares ORDER BY position")
    suspend fun shares(): List<ShareRow>

    @Query("DELETE FROM shares")
    suspend fun clearShares()

    @Insert
    suspend fun insertShares(rows: List<ShareRow>)
}

@Dao
internal interface RecordDao {
    @Query("SELECT * FROM presets ORDER BY position")
    suspend fun presets(): List<PresetRow>

    @Query("DELETE FROM presets")
    suspend fun clearPresets()

    @Insert
    suspend fun insertPresets(rows: List<PresetRow>)

    @Query("SELECT * FROM trigger_records ORDER BY position")
    suspend fun triggers(): List<TriggerRow>

    @Query("DELETE FROM trigger_records")
    suspend fun clearTriggers()

    @Insert
    suspend fun insertTriggers(rows: List<TriggerRow>)

    @Query("SELECT * FROM acknowledgements ORDER BY `key`")
    suspend fun acknowledgements(): List<AcknowledgementRow>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAcknowledgements(rows: List<AcknowledgementRow>)
}

@Dao
internal interface UsageDao {
    @Query("SELECT * FROM usage_records ORDER BY seq")
    suspend fun all(): List<UsageRow>

    @Insert
    suspend fun insert(rows: List<UsageRow>)

    @Query("DELETE FROM usage_records WHERE seq NOT IN (SELECT seq FROM usage_records ORDER BY seq DESC LIMIT :keep)")
    suspend fun keepNewest(keep: Int)
}

@Dao
internal interface MetaDao {
    @Query("SELECT value FROM meta WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(row: MetaRow)
}
