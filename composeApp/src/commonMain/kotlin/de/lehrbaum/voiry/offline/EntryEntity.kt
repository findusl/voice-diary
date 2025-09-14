package de.lehrbaum.voiry.offline

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "entries")
data class EntryEntity(
    @PrimaryKey val id: String,
    val title: String,
    val recordedAt: Long,
    val durationMillis: Long,
    val transcriptionText: String?,
    val transcriptionStatus: String,
    val transcriptionUpdatedAt: Long?,
)
