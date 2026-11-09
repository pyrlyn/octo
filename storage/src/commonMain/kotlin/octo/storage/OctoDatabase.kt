package octo.storage

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

@Database(
    entities = [
        TaskRow::class,
        TranscriptRow::class,
        LayoutRow::class,
        DeviceRow::class,
        ShareRow::class,
        PresetRow::class,
        TriggerRow::class,
        UsageRow::class,
        AcknowledgementRow::class,
        MetaRow::class,
    ],
    version = 1,
)
@ConstructedBy(OctoDatabaseConstructor::class)
internal abstract class OctoDatabase : RoomDatabase() {
    abstract fun state(): StateDao

    abstract fun access(): AccessDao

    abstract fun records(): RecordDao

    abstract fun usage(): UsageDao

    abstract fun meta(): MetaDao
}

@Suppress("KotlinNoActualForExpect")
internal expect object OctoDatabaseConstructor : RoomDatabaseConstructor<OctoDatabase> {
    override fun initialize(): OctoDatabase
}
