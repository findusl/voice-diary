@file:OptIn(ExperimentalUuidApi::class)

package de.lehrbaum.voiry

import de.lehrbaum.voiry.api.v1.DiaryEvent
import de.lehrbaum.voiry.api.v1.TranscriptionStatus
import de.lehrbaum.voiry.api.v1.VoiceDiaryEntry
import io.github.aakira.napier.Napier
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@ExperimentalUuidApi
@ExperimentalTime
class DiaryServiceImpl private constructor(
	private val repository: DiaryRepository,
	initialEntries: List<VoiceDiaryEntry> = emptyList(),
) : DiaryService {
	private val entries = ConcurrentHashMap(initialEntries.associateBy { it.id })
	private val events = MutableSharedFlow<DiaryEvent>(extraBufferCapacity = 64)
	private val mutex = Mutex()

	override fun eventFlow(): Flow<DiaryEvent> =
		flow {
			mutex.withLock {
				emit(DiaryEvent.EntriesSnapshot(entries.values.toList()))
			}
			emitAll(events)
		}

	override suspend fun addEntry(entry: VoiceDiaryEntry, audio: ByteArray) {
		mutex.withLock {
			try {
				repository.add(entry, audio)
				entries[entry.id] = entry
				events.emit(DiaryEvent.EntryCreated(entry))
			} catch (e: Exception) {
				Napier.e("Failed to add entry ${entry.id}", e)
			}
		}
	}

	override suspend fun deleteEntry(id: Uuid) {
		mutex.withLock {
			if (entries.containsKey(id)) {
				try {
					repository.delete(id)
					entries.remove(id)
					events.emit(DiaryEvent.EntryDeleted(id))
				} catch (e: Exception) {
					Napier.e("Failed to delete entry $id", e)
				}
			}
		}
	}

	override suspend fun updateTranscription(
		id: Uuid,
		transcriptionText: String?,
		transcriptionStatus: TranscriptionStatus,
		transcriptionUpdatedAt: Instant?,
	) {
		mutex.withLock {
			val current = entries[id] ?: return@withLock
			val updated = current.copy(
				transcriptionText = transcriptionText,
				transcriptionStatus = transcriptionStatus,
				transcriptionUpdatedAt = transcriptionUpdatedAt,
			)
			try {
				repository.updateTranscription(
					id,
					transcriptionText,
					transcriptionStatus,
					transcriptionUpdatedAt,
				)
				entries[id] = updated
				events.emit(
					DiaryEvent.TranscriptionUpdated(
						id,
						transcriptionText,
						transcriptionStatus,
						transcriptionUpdatedAt,
					),
				)
			} catch (e: Exception) {
				Napier.e("Failed to update transcription for $id", e)
			}
		}
	}

	override suspend fun getAudio(id: Uuid): ByteArray? = mutex.withLock { repository.getAudio(id) }

	companion object Companion {
		suspend fun create(repository: DiaryRepository = DiaryRepository.create()): DiaryServiceImpl {
			val entries = repository.getAll()
			return DiaryServiceImpl(repository, entries)
		}
	}
}
