package de.lehrbaum.voiry.api.v1

import de.lehrbaum.voiry.DiaryRepository
import de.lehrbaum.voiry.DiaryService
import de.lehrbaum.voiry.DiaryServiceImpl
import de.lehrbaum.voiry.audio.AudioCache
import de.lehrbaum.voiry.initLoggingPlatform
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
import io.ktor.server.application.install
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
class DiaryClientTest {
	private lateinit var audioCache: AudioCache

	@BeforeTest
	fun setupLogging() {
		initLoggingPlatform()
		audioCache = AudioCache(Files.createTempDirectory("diaryClientTestCache").toString())
	}

	@Test
	fun `client receives updates and handles duplicates`() =
		testApplication {
			val service = DiaryServiceImpl.create(DiaryRepository(Files.createTempDirectory("clientTest1")))
			application { module(service) }

			createDiaryClientAgainstMockKtorApplication().use { client: DiaryClient ->
				val entry = sampleEntry(Uuid.random())
				val entriesDeferred = CoroutineScope(Dispatchers.Default).async {
					client.entries.filter { it.isNotEmpty() }.first()
				}
				service.addEntry(entry, ByteArray(0))
				val entries = entriesDeferred.await()
				assertEquals(1, entries.size)
			}
		}

	@Test
	fun `client deduplicates snapshot entries`() =
		testApplication {
			val entry = sampleEntry(Uuid.random())
			val service = object : DiaryService {
				override fun eventFlow() = flowOf(DiaryEvent.EntriesSnapshot(listOf(entry, entry)))

				override suspend fun addEntry(entry: VoiceDiaryEntry, audio: ByteArray) { }

				override suspend fun deleteEntry(id: Uuid) { }

				override suspend fun updateTranscription(
					id: Uuid,
					transcriptionText: String?,
					transcriptionStatus: TranscriptionStatus,
					transcriptionUpdatedAt: Instant?,
				) { }

				override suspend fun getAudio(id: Uuid): ByteArray? = null
			}

			application { module(service) }

			createDiaryClientAgainstMockKtorApplication().use { client: DiaryClient ->
				val entries = client.entries.filter { it.isNotEmpty() }.first()
				assertEquals(listOf(entry), entries)
			}
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

			createDiaryClientAgainstMockKtorApplication().use { client: DiaryClient ->
				// should have all events from the second call after reconnect
				val ids = client.entries.filter { it.size > 1 }.first().map { it.id }
				assertTrue(ids.containsAll(listOf(entry1.id, entry2.id)))
			}
		}

	@Test
	fun `create entry success`() =
		testApplication {
			val service = DiaryServiceImpl.create(DiaryRepository(Files.createTempDirectory("clientTestCreate")))
			application { module(service) }
			createDiaryClientAgainstMockKtorApplication().use { client: DiaryClient ->
				val entry = sampleEntry(Uuid.random())
				val result = client.createEntry(entry, ByteArray(0))
				assertEquals(entry, result)
			}
		}

	@Test
	fun `create entry caches audio`() =
		testApplication {
			val entry = sampleEntry(Uuid.random())
			application {
				install(ServerContentNegotiation) { json() }
				routing {
					post("/v1/entries") { call.respond(entry) }
				}
			}
			createDiaryClientAgainstMockKtorApplication().use { client: DiaryClient ->
				val audio = byteArrayOf(7, 8, 9)
				client.createEntry(entry, audio)
				assertContentEquals(audio, audioCache.getAudio(entry.id))
			}
		}

	@Test
	fun `create entry 400`() =
		testApplication {
			application {
				routing {
					post("/v1/entries") { call.respond(HttpStatusCode.BadRequest) }
				}
			}
			createDiaryClientAgainstMockKtorApplication().use { client: DiaryClient ->
				val entry = sampleEntry(Uuid.random())
				assertFailsWith<ClientRequestException> {
					client.createEntry(entry, ByteArray(0))
				}
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
			createDiaryClientAgainstMockKtorApplication().use { client: DiaryClient ->
				val entry = sampleEntry(Uuid.random())
				assertFailsWith<ServerResponseException> {
					client.createEntry(entry, ByteArray(0))
				}
			}
		}

	@Test
	fun `update transcription success`() =
		testApplication {
			val service = DiaryServiceImpl.create(DiaryRepository(Files.createTempDirectory("clientTestUpdate")))
			val entry = sampleEntry(Uuid.random())
			service.addEntry(entry, ByteArray(0))
			application { module(service) }
			createDiaryClientAgainstMockKtorApplication().use { client: DiaryClient ->
				val req = UpdateTranscriptionRequest("text", TranscriptionStatus.DONE, Clock.System.now())
				client.updateTranscription(entry.id, req)
			}
		}

	@Test
	fun `update transcription 404`() =
		testApplication {
			application {
				routing {
					put("/v1/entries/{id}/transcription") { call.respond(HttpStatusCode.NotFound) }
				}
			}
			createDiaryClientAgainstMockKtorApplication().use { client: DiaryClient ->
				val req = UpdateTranscriptionRequest(null, TranscriptionStatus.NONE, null)
				assertFailsWith<ClientRequestException> {
					client.updateTranscription(Uuid.random(), req)
				}
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
			createDiaryClientAgainstMockKtorApplication().use { client: DiaryClient ->
				val req = UpdateTranscriptionRequest(null, TranscriptionStatus.NONE, null)
				assertFailsWith<ServerResponseException> {
					client.updateTranscription(Uuid.random(), req)
				}
			}
		}

	@Test
	fun `delete entry success`() =
		testApplication {
			val service = DiaryServiceImpl.create(DiaryRepository(Files.createTempDirectory("clientTestDelete")))
			val entry = sampleEntry(Uuid.random())
			service.addEntry(entry, ByteArray(0))
			application { module(service) }
			createDiaryClientAgainstMockKtorApplication().use { client: DiaryClient ->
				client.deleteEntry(entry.id)
			}
		}

	@Test
	fun `delete entry 404`() =
		testApplication {
			application {
				routing { delete("/v1/entries/{id}") { call.respond(HttpStatusCode.NotFound) } }
			}
			createDiaryClientAgainstMockKtorApplication().use { client: DiaryClient ->
				assertFailsWith<ClientRequestException> {
					client.deleteEntry(Uuid.random())
				}
			}
		}

	@Test
	fun `delete entry 500`() =
		testApplication {
			application {
				routing { delete("/v1/entries/{id}") { call.respond(HttpStatusCode.InternalServerError) } }
			}
			createDiaryClientAgainstMockKtorApplication().use { client: DiaryClient ->
				assertFailsWith<ServerResponseException> {
					client.deleteEntry(Uuid.random())
				}
			}
		}

	@Test
	fun `get audio success`() =
		testApplication {
			val service = DiaryServiceImpl.create(
				DiaryRepository(Files.createTempDirectory("clientTestGetAudio")),
			)
			val entry = sampleEntry(Uuid.random())
			val audio = byteArrayOf(1, 2, 3)
			service.addEntry(entry, audio)
			application { module(service) }
			createDiaryClientAgainstMockKtorApplication().use { client: DiaryClient ->
				val result = client.getAudio(entry.id)
				assertContentEquals(audio, result)
			}
		}

	@Test
	fun `get audio cached`() =
		testApplication {
			val audio = byteArrayOf(4, 5, 6)
			var callCount = 0
			application {
				routing {
					get("/v1/entries/{id}/audio") {
						callCount++
						call.respondBytes(audio)
					}
				}
			}
			createDiaryClientAgainstMockKtorApplication().use { client: DiaryClient ->
				val id = Uuid.random()
				val first = client.getAudio(id)
				val second = client.getAudio(id)
				assertContentEquals(audio, first)
				assertContentEquals(audio, second)
				assertEquals(1, callCount)
			}
		}

	@Test
	fun `get audio 404`() =
		testApplication {
			application {
				routing { get("/v1/entries/{id}/audio") { call.respond(HttpStatusCode.NotFound) } }
			}
			createDiaryClientAgainstMockKtorApplication().use { client: DiaryClient ->
				assertFailsWith<ClientRequestException> {
					client.getAudio(Uuid.random())
				}
			}
		}

	@Test
	fun `get audio 500`() =
		testApplication {
			application {
				routing {
					get("/v1/entries/{id}/audio") {
						call.respond(HttpStatusCode.InternalServerError)
					}
				}
			}
			createDiaryClientAgainstMockKtorApplication().use { client: DiaryClient ->
				assertFailsWith<ServerResponseException> {
					client.getAudio(Uuid.random())
				}
			}
		}

	private fun ApplicationTestBuilder.createDiaryClientAgainstMockKtorApplication(): DiaryClient =
		DiaryClientImpl(
			baseUrl = "",
			httpClient = createClient {
				install(ContentNegotiation) { json() }

				install(SSE)
			},
			audioCache = audioCache,
		)

	private fun sampleEntry(id: Uuid) =
		VoiceDiaryEntry(
			id = id,
			title = "title$id",
			recordedAt = Clock.System.now(),
			duration = 1.seconds,
			transcriptionStatus = TranscriptionStatus.NONE,
		)
}
