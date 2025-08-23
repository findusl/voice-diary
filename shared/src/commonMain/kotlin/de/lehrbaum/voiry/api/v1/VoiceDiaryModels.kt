package de.lehrbaum.voiry.api.v1

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TranscriptionStatus {
	NONE,
	IN_PROGRESS,
	DONE,
	FAILED
}

/**
 * Representation of a single voice diary entry.
 *
 * All timestamps must be ISO-8601/RFC3339 strings with explicit UTC offset.
 * Duration is encoded in milliseconds.
 */
@Serializable
data class VoiceDiaryEntry(
	val id: String,
	val title: String,
	val recordedAt: String,
	val duration: Long,
	val transcriptionText: String? = null,
	val transcriptionStatus: TranscriptionStatus = TranscriptionStatus.NONE,
	val transcriptionUpdatedAt: String? = null
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
	data class EntryDeleted(val id: String) : DiaryEvent

	@Serializable
	@SerialName("transcriptionUpdated")
	data class TranscriptionUpdated(
		val id: String,
		val transcriptionText: String?,
		val transcriptionStatus: TranscriptionStatus,
		val transcriptionUpdatedAt: String?
	) : DiaryEvent
}

