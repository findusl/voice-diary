@file:OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)

package de.lehrbaum.voiry.api.v1

import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TranscriptionStatus {
	NONE,
	IN_PROGRESS,
	DONE,
	FAILED,
}

/** Representation of a single voice diary entry. */
@Serializable
data class VoiceDiaryEntry(
	val id: Uuid,
	val title: String,
	val recordedAt: Instant,
	val duration: Duration,
	val transcriptionText: String? = null,
	val transcriptionStatus: TranscriptionStatus = TranscriptionStatus.NONE,
	val transcriptionUpdatedAt: Instant? = null,
)

@Serializable
sealed interface DiaryEvent {
	@Serializable
	@SerialName("snapshot")
	data class EntriesSnapshot(val entries: List<VoiceDiaryEntry>) : DiaryEvent

	@Serializable
	@SerialName("entryCreated")
	data class EntryCreated(val entry: VoiceDiaryEntry) : DiaryEvent

	@Serializable
	@SerialName("entryDeleted")
	data class EntryDeleted(val id: Uuid) : DiaryEvent

	@Serializable
	@SerialName("transcriptionUpdated")
	data class TranscriptionUpdated(
		val id: Uuid,
		val transcriptionText: String?,
		val transcriptionStatus: TranscriptionStatus,
		val transcriptionUpdatedAt: Instant?,
	) : DiaryEvent
}
