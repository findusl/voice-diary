package de.lehrbaum.voiry.api.v1

import androidx.compose.runtime.Stable
import de.lehrbaum.voiry.audio.AudioCache
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
import io.ktor.client.request.get
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
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json

/**
 * Client for communicating with the voice diary server.
 *
 * Uses Server-Sent Events to keep [entries] updated.
 */
@Stable
@ExperimentalUuidApi
@ExperimentalTime
class DiaryClientImpl(
	private val baseUrl: String,
	private val httpClient: HttpClient = HttpClient {
		install(ContentNegotiation) { json() }
		install(SSE)
	},
	private val audioCache: AudioCache = AudioCache(),
) : DiaryClient {
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
	private val connectionErrorState = MutableStateFlow<String?>(null)
	override val connectionError: StateFlow<String?> = connectionErrorState.asStateFlow()

	init {
		Napier.d("Created DiaryClient for $baseUrl")
	}

	override val entries: StateFlow<PersistentList<VoiceDiaryEntry>> = flow {
		var retryDelayMillis = 1_000L
		while (currentCoroutineContext().isActive) {
			try {
				httpClient.sse("$baseUrl/v1/entries") {
					connectionErrorState.value = null
					incoming.collect { event ->
						event.data?.let {
							val parsed = Json.decodeFromString(DiaryEvent.serializer(), it)
							emit(parsed)
						}
					}
				}
				Napier.i("SSE connection closed")
				retryDelayMillis = 1_000L
			} catch (e: Exception) {
				if (e is CancellationException) {
					break
				}
				Napier.e("SSE connection failed", e)
				connectionErrorState.value = e.message
				delay(retryDelayMillis)
				retryDelayMillis = (retryDelayMillis * 2).coerceAtMost(60_000L)
			}
		}
	}.runningFold(persistentListOf<VoiceDiaryEntry>()) { list, event -> applyEvent(list, event) }
		.stateIn(scope, SharingStarted.WhileSubscribed(), persistentListOf())

	private val entryFlows = mutableMapOf<Uuid, StateFlow<VoiceDiaryEntry?>>()

	override fun entryFlow(id: Uuid): StateFlow<VoiceDiaryEntry?> =
		entryFlows.getOrPut(id) {
			entries
				.map { list -> list.firstOrNull { it.id == id } }
				.stateIn(scope, SharingStarted.WhileSubscribed(), null)
		}

	override fun close() = scope.cancel()

	override suspend fun createEntry(entry: VoiceDiaryEntry, audio: ByteArray): VoiceDiaryEntry {
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
			audioCache.putAudio(entry.id, audio)
			return response.body()
		} finally {
			parts.forEach { it.dispose() }
		}
	}

	override suspend fun updateTranscription(id: Uuid, request: UpdateTranscriptionRequest) {
		val response = httpClient.put("$baseUrl/v1/entries/$id/transcription") {
			header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
			setBody(request)
		}
		throwIfFailed(response)
	}

	override suspend fun deleteEntry(id: Uuid) {
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

	override suspend fun getAudio(id: Uuid): ByteArray {
		audioCache.getAudio(id)?.let { return it }
		val response = httpClient.get("$baseUrl/v1/entries/$id/audio")
		throwIfFailed(response)
		val bytes: ByteArray = response.body()
		audioCache.putAudio(id, bytes)
		return bytes
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

	private fun applyEvent(list: PersistentList<VoiceDiaryEntry>, event: DiaryEvent): PersistentList<VoiceDiaryEntry> =
		when (event) {
			is DiaryEvent.EntriesSnapshot -> event.entries.toPersistentList()
			is DiaryEvent.EntryCreated ->
				if (list.any { it.id == event.entry.id }) list else list.add(event.entry)
			is DiaryEvent.EntryDeleted -> list.filterNot { it.id == event.id }.toPersistentList()
			is DiaryEvent.TranscriptionUpdated ->
				list
					.map { entry ->
						if (entry.id == event.id) {
							entry.copy(
								transcriptionText = event.transcriptionText,
								transcriptionStatus = event.transcriptionStatus,
								transcriptionUpdatedAt = event.transcriptionUpdatedAt,
							)
						} else {
							entry
						}
					}.toPersistentList()
		}
}
