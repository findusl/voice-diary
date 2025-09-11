package de.lehrbaum.voiry.api.v1

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.collections.immutable.PersistentList
import kotlinx.coroutines.flow.StateFlow

@ExperimentalUuidApi
interface DiaryClient : AutoCloseable {
	val connectionError: StateFlow<String?>
	val entries: StateFlow<PersistentList<VoiceDiaryEntry>>

	fun entryFlow(id: Uuid): StateFlow<VoiceDiaryEntry?>

	suspend fun createEntry(entry: VoiceDiaryEntry, audio: ByteArray): VoiceDiaryEntry

	suspend fun updateTranscription(id: Uuid, request: UpdateTranscriptionRequest)

	suspend fun deleteEntry(id: Uuid)

	suspend fun getAudio(id: Uuid): ByteArray
}
