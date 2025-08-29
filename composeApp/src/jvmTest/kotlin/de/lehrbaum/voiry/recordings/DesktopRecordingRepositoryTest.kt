package de.lehrbaum.voiry.recordings

import de.lehrbaum.voiry.audio.Transcriber
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import kotlinx.io.writeString

class DesktopRecordingRepositoryTest {
	@Test
	fun `saves recording and uses transcriber`() =
		runBlocking {
			var called = 0
			val transcriber = object : Transcriber {
				override suspend fun transcribe(buffer: Buffer): String {
					called++
					return "mock transcript"
				}
			}
			val repo = DesktopRecordingRepository(transcriber)
			val bytes = Buffer().apply { writeString("data") }
			val rec = repo.saveRecording("title", bytes)
			assertEquals("mock transcript", rec.transcript)
			assertEquals(1, called)
		}
}
