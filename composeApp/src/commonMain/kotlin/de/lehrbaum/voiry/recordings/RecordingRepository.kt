package de.lehrbaum.voiry.recordings

import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.io.Buffer
import kotlinx.io.writeString

interface RecordingRepository {
	suspend fun listRecordings(): List<Recording>
	suspend fun saveRecording(bytes: Buffer): Recording
}

class MockRecordingRepository : RecordingRepository {
	private val items = mutableListOf<Recording>()

	init {
		repeat(3) { idx ->
			val buf = Buffer().apply { writeString("Dummy audio #${idx + 1}") }
			items += Recording(
				id = Random.nextLong().toString(),
				title = "Recording ${idx + 1}",
				bytes = buf,
			)
		}
	}

	override suspend fun listRecordings(): List<Recording> {
		// Simulate network latency
		delay(300)
		return items.toList()
	}

	override suspend fun saveRecording(bytes: Buffer): Recording {
		// Simulate upload latency
		delay(500)
		val title = "Recording ${items.size + 1}"
		val rec = Recording(
			id = Random.nextLong().toString(),
			title = title,
			bytes = bytes,
		)
		items.add(0, rec)
		return rec
	}
}
