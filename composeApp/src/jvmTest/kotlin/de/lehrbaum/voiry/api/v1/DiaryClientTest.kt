package de.lehrbaum.voiry.api.v1

import de.lehrbaum.voiry.DiaryEventProvider
import de.lehrbaum.voiry.DiaryService
import de.lehrbaum.voiry.module
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.sse.SSE
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking

class DiaryClientTest {
	@Test
	fun `client receives updates and handles duplicates`() =
		testApplication {
			val service = DiaryService()
			application { module(service) }

			val client = DiaryClient(
				baseUrl = "",
				httpClient = createClient {
					install(ContentNegotiation) { json() }
					install(SSE)
				},
			)
			client.start()

			val entry = sampleEntry("1")
			runBlocking {
				service.addEntry(entry)
				service.addEntry(entry)
				delay(200)
			}

			assertEquals(1, client.entries.value.size)
			client.stop()
		}

	@Test
	fun `client reconnects after drop`() =
		testApplication {
			val entry1 = sampleEntry("1")
			val entry2 = sampleEntry("2")
			val service = DiaryService()
			service.addEntry(entry1)

			val eventProvider = object : DiaryEventProvider {
				var firstCall = true

				override fun eventFlow(): Flow<DiaryEvent> {
					if (firstCall) {
						firstCall = false
						return flowOf(DiaryEvent.EntriesSnapshot(listOf()), DiaryEvent.EntryCreated(entry1))
					} else {
						return service.eventFlow()
					}
				}
			}
			application { module(eventProvider) }

			val client = DiaryClient(
				baseUrl = "",
				httpClient = createClient {
					install(ContentNegotiation) { json() }
					install(SSE)
				},
			)
			client.start()

			// wait for first event processed. Flow should be cut off
			client.entries.filter { it.isNotEmpty() }.first()

			service.addEntry(entry2)

			val ids = client.entries.filter { it.size > 1 }.first().map { it.id }
			assertTrue(ids.containsAll(listOf("1", "2")))
			client.stop()
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
