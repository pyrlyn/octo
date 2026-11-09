package octo.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Nested domain objects that keep growing (a task, a transcript entry, a preset's members) are kept
// in their kotlinx-serialization form, so a new field never needs a migration; flat records get a
// column per field.

@Entity(tableName = "tasks")
internal data class TaskRow(
    @PrimaryKey val id: String,
    val position: Int,
    val body: String,
)

@Entity(
    tableName = "transcript_entries",
    primaryKeys = ["task_id", "seq"],
    indices = [Index("task_id")],
)
internal data class TranscriptRow(
    @ColumnInfo(name = "task_id") val taskId: String,
    val seq: Int,
    val body: String,
)

@Entity(tableName = "layout")
internal data class LayoutRow(
    @PrimaryKey val id: Int = ONLY,
    val body: String,
) {
    companion object {
        const val ONLY = 0
    }
}

@Entity(tableName = "devices")
internal data class DeviceRow(
    @PrimaryKey val id: String,
    val position: Int,
    val name: String,
    @ColumnInfo(name = "token_hash") val tokenHash: String,
)

@Entity(tableName = "shares")
internal data class ShareRow(
    @PrimaryKey val id: String,
    val position: Int,
    @ColumnInfo(name = "task_id") val taskId: String,
    @ColumnInfo(name = "token_hash") val tokenHash: String,
)

@Entity(tableName = "presets")
internal data class PresetRow(
    @PrimaryKey val name: String,
    val position: Int,
    val members: String,
)

@Entity(tableName = "trigger_records")
internal data class TriggerRow(
    @PrimaryKey val id: String,
    val position: Int,
    @ColumnInfo(name = "last_run") val lastRun: Long?,
    val deliveries: String,
)

@Entity(tableName = "usage_records")
internal data class UsageRow(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    @ColumnInfo(name = "at_ms") val atMs: Long,
    val agent: String,
    val model: String?,
    @ColumnInfo(name = "input_tokens") val inputTokens: Long,
    @ColumnInfo(name = "output_tokens") val outputTokens: Long,
    @ColumnInfo(name = "cache_read_tokens") val cacheReadTokens: Long,
    @ColumnInfo(name = "cache_write_tokens") val cacheWriteTokens: Long,
)

@Entity(tableName = "acknowledgements")
internal data class AcknowledgementRow(
    @PrimaryKey val key: String,
)

@Entity(tableName = "meta")
internal data class MetaRow(
    @PrimaryKey val key: String,
    val value: String,
)
