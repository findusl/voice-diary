package de.lehrbaum.voiry.api.v1

import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.sse
import io.ktor.serialization.kotlinx.json.json
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
				println( "SSE connection closed")
			} catch (e: Exception) {
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
					} else entry
				}
			}
		}
	}
}

