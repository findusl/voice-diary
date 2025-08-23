package de.lehrbaum.voiry

import de.lehrbaum.voiry.api.v1.DiaryEvent
import de.lehrbaum.voiry.api.v1.TranscriptionStatus
import de.lehrbaum.voiry.api.v1.VoiceDiaryEntry
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

class DiaryService : DiaryEventProvider {
	private val entries = ConcurrentHashMap<String, VoiceDiaryEntry>()
	private val events = MutableSharedFlow<DiaryEvent>(extraBufferCapacity = 64)

	override fun eventFlow(): Flow<DiaryEvent> =
		flow {
			emit(DiaryEvent.EntriesSnapshot(entries.values.toList()))
			emitAll(events)
		}

	fun getAll(): List<VoiceDiaryEntry> = entries.values.toList()

	suspend fun addEntry(entry: VoiceDiaryEntry) {
		entries[entry.id] = entry
		events.emit(DiaryEvent.EntryCreated(entry))
	}

	suspend fun deleteEntry(id: String) {
		if (entries.remove(id) != null) {
			events.emit(DiaryEvent.EntryDeleted(id))
		}
	}

	suspend fun updateTranscription(
		id: String,
		transcriptionText: String?,
		transcriptionStatus: TranscriptionStatus,
		transcriptionUpdatedAt: String?,
	) {
		val current = entries[id] ?: return
		val updated = current.copy(
			transcriptionText = transcriptionText,
			transcriptionStatus = transcriptionStatus,
			transcriptionUpdatedAt = transcriptionUpdatedAt,
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
	}
}
