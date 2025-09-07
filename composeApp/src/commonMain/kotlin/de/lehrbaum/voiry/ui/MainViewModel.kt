package de.lehrbaum.voiry.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.lehrbaum.voiry.api.v1.DiaryClient
import de.lehrbaum.voiry.api.v1.TranscriptionStatus
import de.lehrbaum.voiry.api.v1.UpdateTranscriptionRequest
import de.lehrbaum.voiry.api.v1.VoiceDiaryEntry
import de.lehrbaum.voiry.audio.Recorder
import de.lehrbaum.voiry.audio.Transcriber
import de.lehrbaum.voiry.audio.platformRecorder
import io.github.aakira.napier.Napier
import java.io.Closeable
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.io.Buffer
import kotlinx.io.readByteArray

@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
class MainViewModel(
	private val diaryClient: DiaryClient,
	private val recorder: Recorder = platformRecorder,
	private val transcriber: Transcriber?,
) : ViewModel(), Closeable {
	private val baseState = MutableStateFlow(MainUiState(recorderAvailable = recorder.isAvailable))
	val uiState: StateFlow<MainUiState> =
		combine(
			baseState,
			diaryClient.entries.map { entries -> entries.map { it.toUi() } },
			diaryClient.connectionError,
		) { state, entries, error ->
			state.copy(entries = entries, error = error)
		}.stateIn(
			viewModelScope,
			SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
			baseState.value,
		)

	override fun onCleared() {
		super.onCleared()
		close()
	}

	override fun close() {
		runCatching { recorder.close() }
			.onFailure { Napier.e("Recorder close failed", it) }
	}

	fun startRecording() {
		runCatching { recorder.startRecording() }
			.onSuccess {
				baseState.update { it.copy(isRecording = true, error = null) }
			}.onFailure { e ->
				baseState.update {
					it.copy(isRecording = false, error = e.message ?: "Failed to start recording")
				}
			}
	}

	fun stopRecording() {
		viewModelScope.launch {
			val stopResult = recorder.stopRecording()
			stopResult
				.onSuccess { buffer ->
					val bytes = buffer.readByteArray()
					baseState.update {
						it.copy(
							pendingRecording = Recording(bytes),
							isRecording = false,
							error = null,
						)
					}
				}.onFailure { e ->
					baseState.update { it.copy(isRecording = false, error = e.message) }
				}
		}
	}

	fun dismissRecorderUnavailable() {
		baseState.update { it.copy(recorderUnavailableDismissed = true) }
	}

	fun updatePendingTitle(title: String) {
		baseState.update { it.copy(pendingTitle = title) }
	}

	fun cancelSaveRecording() {
		baseState.update { it.copy(pendingRecording = null, pendingTitle = "") }
	}

	fun saveRecording() {
		val recording = baseState.value.pendingRecording ?: return
		val title = baseState.value.pendingTitle
		viewModelScope.launch {
			val bytes = recording.data
			val entry = VoiceDiaryEntry(
				id = Uuid.random(),
				title = title,
				recordedAt = Clock.System.now(),
				duration = Duration.ZERO,
			)
			runCatching { diaryClient.createEntry(entry, bytes) }
				.onFailure { e -> baseState.update { it.copy(error = e.message) } }
			baseState.update { it.copy(pendingRecording = null, pendingTitle = "") }
		}
	}

	fun deleteEntry(entry: UiVoiceDiaryEntry) {
		viewModelScope.launch {
			runCatching { diaryClient.deleteEntry(entry.id) }
				.onFailure { e -> baseState.update { it.copy(error = e.message) } }
		}
	}

	fun transcribe(entry: UiVoiceDiaryEntry) {
		val transcriber = transcriber ?: return
		viewModelScope.launch {
			runCatching {
				val bytes = diaryClient.getAudio(entry.id)
				val buffer = Buffer().apply { write(bytes) }
				val text = transcriber.transcribe(buffer)
				diaryClient.updateTranscription(
					entry.id,
					UpdateTranscriptionRequest(
						text,
						TranscriptionStatus.DONE,
						Clock.System.now(),
					),
				)
			}.onFailure { e ->
				baseState.update { it.copy(error = e.message) }
				runCatching {
					diaryClient.updateTranscription(
						entry.id,
						UpdateTranscriptionRequest(
							null,
							TranscriptionStatus.FAILED,
							Clock.System.now(),
						),
					)
				}
			}
		}
	}
}

@OptIn(ExperimentalUuidApi::class)
data class MainUiState(
	val entries: List<UiVoiceDiaryEntry> = emptyList(),
	val isRecording: Boolean = false,
	val pendingRecording: Recording? = null,
	val pendingTitle: String = "",
	val error: String? = null,
	val recorderAvailable: Boolean = true,
	val recorderUnavailableDismissed: Boolean = false,
)

@Immutable
class Recording(val data: ByteArray)
