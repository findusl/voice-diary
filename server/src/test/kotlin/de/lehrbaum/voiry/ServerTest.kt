package de.lehrbaum.voiry

import de.lehrbaum.voiry.api.v1.DiaryEvent
import de.lehrbaum.voiry.api.v1.TranscriptionStatus
import de.lehrbaum.voiry.api.v1.VoiceDiaryEntry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.sse
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import io.ktor.sse.ServerSentEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

class ServerTest {
	@Test
	fun `sse emits snapshot and updates`() =
		testApplication {
			val service = DiaryService()
			application { module(service) }

			val client = createClient {
				install(ContentNegotiation) { json() }
				install(SSE)
			}

			val events = mutableListOf<DiaryEvent>()
			runBlocking {
				client.sse("/v1/entries") {
					val event: ServerSentEvent = incoming.first()
					events += Json.decodeFromString(DiaryEvent.serializer(), event.data!!)
				}
			}

			assertEquals(listOf<DiaryEvent>(DiaryEvent.EntriesSnapshot(emptyList())), events)
		}

	private fun sampleEntry(id: String) =
		VoiceDiaryEntry(
			id = id,
			title = "title$id",
			recordedAt = "2025-08-23T10:15:30+02:00",
			duration = 1000L,
			transcriptionStatus = TranscriptionStatus.NONE,
		)
}
