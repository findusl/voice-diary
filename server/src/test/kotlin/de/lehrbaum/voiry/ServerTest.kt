package de.lehrbaum.voiry

import de.lehrbaum.voiry.api.v1.DiaryEvent
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.sse
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.sse.ServerSentEvent
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
class ServerTest {
	@Test
	fun `sse emits snapshot and updates`() =
		runBlocking {
			val service = DiaryServiceImpl.create(DiaryRepository(Files.createTempDirectory("serverTest")))
			val server = embeddedServer(Netty, port = 0, host = "127.0.0.1") { module(service) }.start()

			val client = HttpClient(CIO) {
				install(ContentNegotiation) { json() }
				install(SSE)
			}
			try {
				val port = server.engine.resolvedConnectors().single().port
				val events = mutableListOf<DiaryEvent>()
				client.sse("http://127.0.0.1:$port/v1/entries") {
					val event: ServerSentEvent = incoming.first()
					events += Json.decodeFromString(DiaryEvent.serializer(), event.data!!)
				}

				assertEquals(listOf<DiaryEvent>(DiaryEvent.EntriesSnapshot(emptyList())), events)
			} finally {
				client.close()
				server.stop(0, 0)
			}
		}
}
