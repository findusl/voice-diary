package de.lehrbaum.voiry

import de.lehrbaum.voiry.api.v1.DiaryEvent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.sse
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import io.ktor.sse.ServerSentEvent
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
class ServerTest {
	@Test
	fun `sse emits snapshot and updates`() =
		testApplication {
			val service = DiaryServiceImpl.create(DiaryRepository(Files.createTempDirectory("serverTest")))
			application { module(service) }

			val client = createClient {
				install(ContentNegotiation) { json() }
				install(SSE)
			}

			val events = mutableListOf<DiaryEvent>()
			client.sse("/v1/entries") {
				val event: ServerSentEvent = incoming.first()
				events += Json.decodeFromString(DiaryEvent.serializer(), event.data!!)
			}

			assertEquals(listOf<DiaryEvent>(DiaryEvent.EntriesSnapshot(emptyList())), events)
		}
}
