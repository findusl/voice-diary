package de.lehrbaum.voiry.api.v1

import de.lehrbaum.voiry.DiaryRepository
import de.lehrbaum.voiry.DiaryService
import de.lehrbaum.voiry.DiaryServiceImpl
import de.lehrbaum.voiry.initLogging
import de.lehrbaum.voiry.module
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.sse.SSE
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
class DiaryClientTest {
	@BeforeTest
	fun setupLogging() {
		initLogging()
	}

	@Test
	fun `client receives updates and handles duplicates`() =
		testApplication {
			val service = runBlocking { DiaryServiceImpl.create(DiaryRepository(Files.createTempDirectory("clientTest1"))) }
			application { module(service) }

			val client = DiaryClient(
				baseUrl = "",
				httpClient = createClient {
					install(ContentNegotiation) { json() }
					install(SSE)
				},
			)
			client.start()

			val entry = sampleEntry(Uuid.random())
			runBlocking {
				service.addEntry(entry, ByteArray(0))
				delay(200)
			}

			assertEquals(1, client.entries.value.size)
			client.stop()
		}

	@Test
	fun `client reconnects after drop`() =
		testApplication {
			val entry1 = sampleEntry(Uuid.random())
			val entry2 = sampleEntry(Uuid.random())
			val sourceOfContinuousEvents = MutableSharedFlow<DiaryEvent>(replay = 1)
			sourceOfContinuousEvents.emit(DiaryEvent.EntryCreated(entry2))

			val service = mock<DiaryService> {
				every { eventFlow() }.calls { _ ->
					// return a flow that will terminate after the first event
					flowOf(DiaryEvent.EntriesSnapshot(listOf()), DiaryEvent.EntryCreated(entry1))
						.also {
							// override the mock for the next call to return a normal flow that will continue
							every { eventFlow() }.returns(
								flow {
									emit(DiaryEvent.EntriesSnapshot(listOf(entry1)))
									emitAll(sourceOfContinuousEvents)
								},
							)
						}
				}
			}
			application { module(service) }

			val client = DiaryClient(
				baseUrl = "",
				httpClient = createClient {
					install(ContentNegotiation) { json() }
					install(SSE)
				},
			)
			client.start()

			// should have all events from the second call after reconnect
			val ids = client.entries.filter { it.size > 1 }.first().map { it.id }
			assertTrue(ids.containsAll(listOf(entry1.id, entry2.id)))
			client.stop()
		}

	private fun sampleEntry(id: Uuid) =
		VoiceDiaryEntry(
			id = id,
			title = "title$id",
			recordedAt = Clock.System.now(),
			duration = 1.seconds,
			transcriptionStatus = TranscriptionStatus.NONE,
		)
}
