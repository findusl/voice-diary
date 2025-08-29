package de.lehrbaum.voiry.recordings

import de.lehrbaum.voiry.audio.Transcriber
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.write

/** Simple in-memory repository that transcribes recordings using whisper-cli. */
class DesktopRecordingRepository(
	private val transcriber: Transcriber,
) : RecordingRepository {
	private val items = mutableListOf<Recording>()

	override suspend fun listRecordings(): List<Recording> = items.toList()

	override suspend fun saveRecording(title: String, bytes: Buffer): Recording {
		val data = bytes.readByteArray()
		val transcript = runCatching {
			withContext(Dispatchers.IO) {
				transcriber.transcribe(Buffer().apply { write(data) })
			}
		}.getOrDefault("")
		val stored = Buffer().apply { write(data) }
		val rec = Recording(
			id = Random.nextLong().toString(),
			title = title,
			transcript = transcript,
			bytes = stored,
		)
		items.add(0, rec)
		return rec
	}

	override suspend fun deleteRecording(id: String) {
		items.removeAll { it.id == id }
	}
}
