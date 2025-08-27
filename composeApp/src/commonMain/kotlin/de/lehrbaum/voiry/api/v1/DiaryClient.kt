package de.lehrbaum.voiry.api.v1

import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.CancellationException
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Client for communicating with the voice diary server.
 *
 * Call [start] to subscribe to server sent events.
 */
@ExperimentalUuidApi
@ExperimentalTime
class DiaryClient(
	private val baseUrl: String,
	private val httpClient: HttpClient = HttpClient {
		install(ContentNegotiation) { json() }
		install(SSE)
	},
) {
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

	private val _entries = MutableStateFlow<List<VoiceDiaryEntry>>(emptyList())
	val entries: StateFlow<List<VoiceDiaryEntry>> = _entries

	suspend fun createEntry(entry: VoiceDiaryEntry, audio: ByteArray): VoiceDiaryEntry {
		val parts = formData {
			append(
				"metadata",
				Json.encodeToString(VoiceDiaryEntry.serializer(), entry),
				Headers.build {
					append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
					append(
						HttpHeaders.ContentDisposition,
						"form-data; name=\"metadata\"",
					)
				},
			)
			append(
				"audio",
				audio,
				Headers.build {
					append(
						HttpHeaders.ContentType,
						ContentType("audio", "wav").toString(),
					)
					append(
						HttpHeaders.ContentDisposition,
						"form-data; name=\"audio\"; filename=\"audio.wav\"",
					)
				},
			)
		}
		try {
			val response = httpClient.post("$baseUrl/v1/entries") {
				setBody(MultiPartFormDataContent(parts))
			}
			throwIfFailed(response)
			return response.body()
		} finally {
			parts.forEach { it.dispose() }
		}
	}

	suspend fun updateTranscription(id: Uuid, request: UpdateTranscriptionRequest) {
		val response = httpClient.put("$baseUrl/v1/entries/$id/transcription") {
			header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
			setBody(request)
		}
		if (!response.status.isSuccess()) {
			val text = response.bodyAsText()
			if (response.status.value in 400..499) {
				throw ClientRequestException(response, text)
			} else {
				throw ServerResponseException(response, text)
			}
		}
	}

	suspend fun deleteEntry(id: Uuid) {
		val response = httpClient.delete("$baseUrl/v1/entries/$id")
		if (!response.status.isSuccess()) {
			val text = response.bodyAsText()
			if (response.status.value in 400..499) {
				throw ClientRequestException(response, text)
			} else {
				throw ServerResponseException(response, text)
			}
		}
	}

	private suspend fun throwIfFailed(response: HttpResponse) {
		if (!response.status.isSuccess()) {
			val text = response.bodyAsText()
			if (response.status.value in 400..499) {
				throw ClientRequestException(response, text)
			} else {
				throw ServerResponseException(response, text)
			}
		}
	}

	fun start() {
		scope.launch { sseLoop() }
	}

	fun stop() {
		scope.cancel()
	}

	private suspend fun sseLoop() {
		while (scope.isActive) {
			try {
				httpClient.sse("$baseUrl/v1/entries") {
					incoming.collect { event ->
						event.data?.let {
							val parsed = Json.decodeFromString(DiaryEvent.serializer(), it)
							applyEvent(parsed)
						}
					}
				}
				Napier.i("SSE connection closed")
			} catch (e: Exception) {
				if (e is CancellationException) {
					return
				}
				Napier.e("SSE connection failed", e)
			}
		}
	}

	private fun applyEvent(event: DiaryEvent) {
		when (event) {
			is DiaryEvent.EntriesSnapshot -> _entries.value = event.entries
			is DiaryEvent.EntryCreated -> _entries.update { list ->
				if (list.any { it.id == event.entry.id }) list else list + event.entry
			}

			is DiaryEvent.EntryDeleted -> _entries.update { list ->
				list.filterNot { it.id == event.id }
			}

			is DiaryEvent.TranscriptionUpdated -> _entries.update { list ->
				list.map { entry ->
					if (entry.id == event.id) {
						entry.copy(
							transcriptionText = event.transcriptionText,
							transcriptionStatus = event.transcriptionStatus,
							transcriptionUpdatedAt = event.transcriptionUpdatedAt,
						)
					} else {
						entry
					}
				}
			}
		}
	}
}
