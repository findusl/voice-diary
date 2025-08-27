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
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.sse.SSE
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

			val client = createDiaryClientAgainstMockKtorApplication()
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

	@Test
	fun `create entry success`() =
		testApplication {
			val service = runBlocking {
				DiaryServiceImpl.create(DiaryRepository(Files.createTempDirectory("clientTestCreate")))
			}
			application { module(service) }
			val client = createDiaryClientAgainstMockKtorApplication()
			val entry = sampleEntry(Uuid.random())
			val result = runBlocking { client.createEntry(entry, ByteArray(0)) }
			assertEquals(entry, result)
		}

	@Test
	fun `create entry 400`() =
		testApplication {
			application {
				routing {
					post("/v1/entries") { call.respond(HttpStatusCode.BadRequest) }
				}
			}
			val client = createDiaryClientAgainstMockKtorApplication()
			val entry = sampleEntry(Uuid.random())
			assertFailsWith<ClientRequestException> {
				runBlocking { client.createEntry(entry, ByteArray(0)) }
			}
		}

	@Test
	fun `create entry 500`() =
		testApplication {
			application {
				routing {
					post("/v1/entries") { call.respond(HttpStatusCode.InternalServerError) }
				}
			}
			val client = createDiaryClientAgainstMockKtorApplication()
			val entry = sampleEntry(Uuid.random())
			assertFailsWith<ServerResponseException> {
				runBlocking { client.createEntry(entry, ByteArray(0)) }
			}
		}

	@Test
	fun `update transcription success`() =
		testApplication {
			val service = runBlocking {
				DiaryServiceImpl.create(DiaryRepository(Files.createTempDirectory("clientTestUpdate")))
			}
			val entry = sampleEntry(Uuid.random())
			runBlocking { service.addEntry(entry, ByteArray(0)) }
			application { module(service) }
			val client = createDiaryClientAgainstMockKtorApplication()
			val req = UpdateTranscriptionRequest("text", TranscriptionStatus.DONE, Clock.System.now())
			runBlocking { client.updateTranscription(entry.id, req) }
		}

	@Test
	fun `update transcription 404`() =
		testApplication {
			application {
				routing {
					put("/v1/entries/{id}/transcription") { call.respond(HttpStatusCode.NotFound) }
				}
			}
			val client = DiaryClient(
				baseUrl = "",
				httpClient = createClient { install(ContentNegotiation) { json() } },
			)
			val req = UpdateTranscriptionRequest(null, TranscriptionStatus.NONE, null)
			assertFailsWith<ClientRequestException> {
				runBlocking { client.updateTranscription(Uuid.random(), req) }
			}
		}

	@Test
	fun `update transcription 500`() =
		testApplication {
			application {
				routing {
					put("/v1/entries/{id}/transcription") { call.respond(HttpStatusCode.InternalServerError) }
				}
			}
			val client = createDiaryClientAgainstMockKtorApplication()
			val req = UpdateTranscriptionRequest(null, TranscriptionStatus.NONE, null)
			assertFailsWith<ServerResponseException> {
				runBlocking { client.updateTranscription(Uuid.random(), req) }
			}
		}

	@Test
	fun `delete entry success`() =
		testApplication {
			val service = runBlocking {
				DiaryServiceImpl.create(DiaryRepository(Files.createTempDirectory("clientTestDelete")))
			}
			val entry = sampleEntry(Uuid.random())
			runBlocking { service.addEntry(entry, ByteArray(0)) }
			application { module(service) }
			val client = createDiaryClientAgainstMockKtorApplication()
			runBlocking { client.deleteEntry(entry.id) }
		}

	@Test
	fun `delete entry 404`() =
		testApplication {
			application {
				routing { delete("/v1/entries/{id}") { call.respond(HttpStatusCode.NotFound) } }
			}
			val client = createDiaryClientAgainstMockKtorApplication()
			assertFailsWith<ClientRequestException> {
				runBlocking { client.deleteEntry(Uuid.random()) }
			}
		}

	private fun ApplicationTestBuilder.createDiaryClientAgainstMockKtorApplication(): DiaryClient =
		DiaryClient(
			baseUrl = "",
			httpClient = createClient {
				install(ContentNegotiation) { json() }

				install(SSE)
			},
		)

	@Test
	fun `delete entry 500`() =
		testApplication {
			application {
				routing { delete("/v1/entries/{id}") { call.respond(HttpStatusCode.InternalServerError) } }
			}
			val client = createDiaryClientAgainstMockKtorApplication()
			assertFailsWith<ServerResponseException> {
				runBlocking { client.deleteEntry(Uuid.random()) }
			}
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
