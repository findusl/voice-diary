package de.lehrbaum.voiry.offline

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_events")
data class PendingEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: PendingEventType,
    @ColumnInfo(name = "entry_id") val entryId: String,
    val payload: String,
    @ColumnInfo(name = "queued_at") val queuedAt: Long,
)

enum class PendingEventType {
    CREATE,
    UPDATE_TRANSCRIPTION,
    DELETE,
}
