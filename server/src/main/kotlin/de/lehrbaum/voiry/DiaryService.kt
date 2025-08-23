package de.lehrbaum.voiry

import de.lehrbaum.voiry.api.v1.DiaryEvent
import de.lehrbaum.voiry.api.v1.TranscriptionStatus
import de.lehrbaum.voiry.api.v1.VoiceDiaryEntry
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow

@ExperimentalUuidApi
@ExperimentalTime
interface DiaryService {
	fun eventFlow(): Flow<DiaryEvent>

	suspend fun addEntry(entry: VoiceDiaryEntry, audio: ByteArray)

	suspend fun deleteEntry(id: Uuid)

	suspend fun updateTranscription(
		id: Uuid,
		transcriptionText: String?,
		transcriptionStatus: TranscriptionStatus,
		transcriptionUpdatedAt: Instant?,
	)

	suspend fun getAudio(id: Uuid): ByteArray?
}
