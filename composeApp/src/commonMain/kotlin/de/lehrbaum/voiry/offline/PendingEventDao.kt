package de.lehrbaum.voiry.offline

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingEventDao {
    @Query("SELECT * FROM pending_events ORDER BY id ASC")
    fun observeAll(): Flow<List<PendingEventEntity>>

    @Insert
    suspend fun enqueue(event: PendingEventEntity): Long

    @Query("DELETE FROM pending_events WHERE id = :id")
    suspend fun remove(id: Long)
}
