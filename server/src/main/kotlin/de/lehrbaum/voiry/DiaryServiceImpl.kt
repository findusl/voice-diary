@file:OptIn(ExperimentalUuidApi::class)

package de.lehrbaum.voiry

import de.lehrbaum.voiry.api.v1.DiaryEvent
import de.lehrbaum.voiry.api.v1.TranscriptionStatus
import de.lehrbaum.voiry.api.v1.VoiceDiaryEntry
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

@ExperimentalUuidApi
@ExperimentalTime
class DiaryServiceImpl private constructor(
	private val repository: DiaryRepository,
	initialEntries: List<VoiceDiaryEntry> = emptyList(),
) : DiaryService {
	private val entries = ConcurrentHashMap(initialEntries.associateBy { it.id })
	private val events = MutableSharedFlow<DiaryEvent>(extraBufferCapacity = 64)

	override fun eventFlow(): Flow<DiaryEvent> =
		flow {
			emit(DiaryEvent.EntriesSnapshot(entries.values.toList()))
			emitAll(events)
		}

	override suspend fun addEntry(entry: VoiceDiaryEntry, audio: ByteArray) {
		entries[entry.id] = entry
		repository.add(entry, audio)
		events.emit(DiaryEvent.EntryCreated(entry))
	}

	override suspend fun deleteEntry(id: Uuid) {
		if (entries.remove(id) != null) {
			repository.delete(id)
			events.emit(DiaryEvent.EntryDeleted(id))
		}
	}

	override suspend fun updateTranscription(
		id: Uuid,
		transcriptionText: String?,
		transcriptionStatus: TranscriptionStatus,
		transcriptionUpdatedAt: Instant?,
	) {
		val current = entries[id] ?: return
		val updated = current.copy(
			transcriptionText = transcriptionText,
			transcriptionStatus = transcriptionStatus,
			transcriptionUpdatedAt = transcriptionUpdatedAt,
		)
		entries[id] = updated
		repository.updateTranscription(id, transcriptionText, transcriptionStatus, transcriptionUpdatedAt)
		events.emit(
			DiaryEvent.TranscriptionUpdated(
				id,
				transcriptionText,
				transcriptionStatus,
				transcriptionUpdatedAt,
			),
		)
	}

	override suspend fun getAudio(id: Uuid): ByteArray? = repository.getAudio(id)

	companion object Companion {
		suspend fun create(repository: DiaryRepository = DiaryRepository.create()): DiaryServiceImpl {
			val entries = repository.getAll()
			return DiaryServiceImpl(repository, entries)
		}
	}
}
