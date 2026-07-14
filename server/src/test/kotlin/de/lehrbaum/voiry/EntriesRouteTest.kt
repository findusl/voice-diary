package de.lehrbaum.voiry

import de.lehrbaum.voiry.api.v1.TranscriptionStatus
import de.lehrbaum.voiry.api.v1.UpdateTranscriptionRequest
import de.lehrbaum.voiry.api.v1.VoiceDiaryEntry
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.json.Json

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
class EntriesRouteTest {
	@Test
	fun `entry lifecycle supports create audio update and delete`() =
		testApplication {
			val repository = DiaryRepository(Files.createTempDirectory("entriesRouteTest"))
			val service = DiaryServiceImpl.create(repository)
			application { module(service) }
			val apiClient = createClient {
				install(ContentNegotiation) { json() }
			}
			val entry = testEntry()
			val audio = byteArrayOf(1, 2, 3, 4)

			val createResponse = apiClient.post("/v1/entries") {
				setBody(entryMultipart(Json.encodeToString(VoiceDiaryEntry.serializer(), entry), audio))
			}

			assertEquals(HttpStatusCode.OK, createResponse.status)
			assertEquals(entry, createResponse.body<VoiceDiaryEntry>())

			val audioResponse = apiClient.get("/v1/entries/${entry.id}/audio")
			assertEquals(HttpStatusCode.OK, audioResponse.status)
			assertEquals("audio/wav", audioResponse.headers[HttpHeaders.ContentType])
			assertContentEquals(audio, audioResponse.body<ByteArray>())

			val updatedAt = Instant.parse("2026-07-14T12:30:00Z")
			val updateResponse = apiClient.put("/v1/entries/${entry.id}/transcription") {
				contentType(ContentType.Application.Json)
				setBody(
					UpdateTranscriptionRequest(
						transcriptionText = "Updated transcription",
						transcriptionStatus = TranscriptionStatus.DONE,
						transcriptionUpdatedAt = updatedAt,
					),
				)
			}
			assertEquals(HttpStatusCode.OK, updateResponse.status)
			assertEquals(
				entry.copy(
					transcriptionText = "Updated transcription",
					transcriptionStatus = TranscriptionStatus.DONE,
					transcriptionUpdatedAt = updatedAt,
				),
				repository.getAll().single(),
			)

			val deleteResponse = apiClient.delete("/v1/entries/${entry.id}")
			assertEquals(HttpStatusCode.NoContent, deleteResponse.status)
			assertTrue(repository.getAll().isEmpty())
			assertEquals(
				HttpStatusCode.NotFound,
				apiClient.get("/v1/entries/${entry.id}/audio").status,
			)
		}

	@Test
	fun `replaying an entry create is idempotent and conflicting metadata is rejected`() =
		testApplication {
			val repository = DiaryRepository(Files.createTempDirectory("idempotentEntryRouteTest"))
			val service = DiaryServiceImpl.create(repository)
			application { module(service) }
			val apiClient = createClient {
				install(ContentNegotiation) { json() }
			}
			val entry = testEntry()
			val originalAudio = byteArrayOf(1, 2, 3)

			val firstResponse = apiClient.post("/v1/entries") {
				setBody(entryMultipart(Json.encodeToString(VoiceDiaryEntry.serializer(), entry), originalAudio))
			}
			val replayResponse = apiClient.post("/v1/entries") {
				setBody(entryMultipart(Json.encodeToString(VoiceDiaryEntry.serializer(), entry), originalAudio))
			}
			val audioConflictResponse = apiClient.post("/v1/entries") {
				setBody(entryMultipart(Json.encodeToString(VoiceDiaryEntry.serializer(), entry), byteArrayOf(9)))
			}
			val conflictResponse = apiClient.post("/v1/entries") {
				val conflict = entry.copy(title = "Different metadata")
				setBody(entryMultipart(Json.encodeToString(VoiceDiaryEntry.serializer(), conflict), byteArrayOf(8)))
			}

			assertEquals(HttpStatusCode.OK, firstResponse.status)
			assertEquals(HttpStatusCode.OK, replayResponse.status)
			assertEquals(HttpStatusCode.Conflict, audioConflictResponse.status)
			assertEquals(HttpStatusCode.Conflict, conflictResponse.status)
			assertEquals(listOf(entry), repository.getAll())
			assertContentEquals(originalAudio, repository.getAudio(entry.id))
		}

	@Test
	fun `missing entries use existing no-op semantics`() =
		testApplication {
			val service = DiaryServiceImpl.create(DiaryRepository(Files.createTempDirectory("missingRouteTest")))
			application { module(service) }
			val apiClient = createClient {
				install(ContentNegotiation) { json() }
			}
			val id = Uuid.random()

			assertEquals(HttpStatusCode.NotFound, apiClient.get("/v1/entries/$id/audio").status)
			assertEquals(
				HttpStatusCode.OK,
				apiClient
					.put("/v1/entries/$id/transcription") {
						contentType(ContentType.Application.Json)
						setBody(
							UpdateTranscriptionRequest(
								transcriptionText = null,
								transcriptionStatus = TranscriptionStatus.FAILED,
								transcriptionUpdatedAt = null,
							),
						)
					}.status,
			)
			assertEquals(HttpStatusCode.NoContent, apiClient.delete("/v1/entries/$id").status)
		}

	@Test
	fun `malformed entry ids return bad request`() =
		testApplication {
			val service = DiaryServiceImpl.create(DiaryRepository(Files.createTempDirectory("malformedIdTest")))
			application { module(service) }
			val malformedId = "not-a-uuid"

			assertEquals(
				HttpStatusCode.BadRequest,
				client.put("/v1/entries/$malformedId/transcription").status,
			)
			assertEquals(HttpStatusCode.BadRequest, client.delete("/v1/entries/$malformedId").status)
			assertEquals(HttpStatusCode.BadRequest, client.get("/v1/entries/$malformedId/audio").status)
		}

	@Test
	fun `invalid entry multipart returns bad request`() =
		testApplication {
			val service = DiaryServiceImpl.create(DiaryRepository(Files.createTempDirectory("invalidMultipartTest")))
			application { module(service) }
			val entryJson = Json.encodeToString(VoiceDiaryEntry.serializer(), testEntry())

			assertEquals(
				HttpStatusCode.BadRequest,
				client
					.post("/v1/entries") {
						setBody(entryMultipart(entryJson, audio = null))
					}.status,
			)
			assertEquals(
				HttpStatusCode.BadRequest,
				client
					.post("/v1/entries") {
						setBody(entryMultipart("not-json", byteArrayOf(1, 2, 3)))
					}.status,
			)
		}

	private fun testEntry() =
		VoiceDiaryEntry(
			id = Uuid.random(),
			title = "Route test",
			recordedAt = Instant.parse("2026-07-14T12:00:00Z"),
			duration = 42.seconds,
		)

	private fun entryMultipart(metadata: String?, audio: ByteArray?) =
		MultiPartFormDataContent(
			formData {
				if (metadata != null) {
					append(
						"metadata",
						metadata,
						Headers.build {
							append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
							append(HttpHeaders.ContentDisposition, "form-data; name=\"metadata\"")
						},
					)
				}
				if (audio != null) {
					append(
						"audio",
						audio,
						Headers.build {
							append(HttpHeaders.ContentType, "audio/wav")
							append(
								HttpHeaders.ContentDisposition,
								"form-data; name=\"audio\"; filename=\"audio.wav\"",
							)
						},
					)
				}
			},
		)
}
