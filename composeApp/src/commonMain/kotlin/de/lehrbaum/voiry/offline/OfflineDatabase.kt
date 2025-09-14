package de.lehrbaum.voiry.offline

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

@Database(
    entities = [EntryEntity::class, PendingEventEntity::class],
    version = 1,
    exportSchema = false,
)
@ConstructedBy(OfflineDatabaseConstructor::class)
abstract class OfflineDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun pendingEventDao(): PendingEventDao
}

@Suppress("KotlinNoActualForExpect")
expect object OfflineDatabaseConstructor : RoomDatabaseConstructor<OfflineDatabase> {
    override fun initialize(): OfflineDatabase
}
