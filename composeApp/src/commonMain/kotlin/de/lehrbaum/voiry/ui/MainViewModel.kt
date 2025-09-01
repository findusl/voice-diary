package de.lehrbaum.voiry.ui

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.lehrbaum.voiry.api.v1.DiaryClient
import de.lehrbaum.voiry.api.v1.TranscriptionStatus
import de.lehrbaum.voiry.api.v1.UpdateTranscriptionRequest
import de.lehrbaum.voiry.api.v1.VoiceDiaryEntry
import de.lehrbaum.voiry.audio.Recorder
import de.lehrbaum.voiry.audio.Transcriber
import de.lehrbaum.voiry.audio.isWhisperAvailable
import de.lehrbaum.voiry.audio.platformRecorder
import de.lehrbaum.voiry.audio.platformTranscriber
import io.github.aakira.napier.Napier
import java.io.Closeable
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.io.Buffer
import kotlinx.io.readByteArray

@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
class MainViewModel(
	private val diaryClient: DiaryClient,
	private val recorder: Recorder = platformRecorder,
	private val transcriber: Transcriber? = platformTranscriber,
) : ViewModel(), Closeable {
	private val _uiState = MutableStateFlow(MainUiState(recorderAvailable = recorder.isAvailable))
	val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

	init {
		viewModelScope.launch {
			diaryClient.entries.collect { entries ->
				_uiState.update { it.copy(entries = entries) }
			}
		}
		viewModelScope.launch {
			val available = transcriber != null && isWhisperAvailable()
			_uiState.update { it.copy(canTranscribe = available) }
		}
	}

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
				_uiState.update { it.copy(isRecording = true, error = null) }
			}.onFailure { e ->
				_uiState.update {
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
					_uiState.update {
						it.copy(
							pendingRecording = Recording(bytes),
							isRecording = false,
							error = null,
						)
					}
				}.onFailure { e ->
					_uiState.update { it.copy(isRecording = false, error = e.message) }
				}
		}
	}

	fun updatePendingTitle(title: String) {
		_uiState.update { it.copy(pendingTitle = title) }
	}

	fun cancelSaveRecording() {
		_uiState.update { it.copy(pendingRecording = null, pendingTitle = "") }
	}

	fun saveRecording() {
		val recording = _uiState.value.pendingRecording ?: return
		val title = _uiState.value.pendingTitle
		viewModelScope.launch {
			val bytes = recording.data
			val entry = VoiceDiaryEntry(
				id = Uuid.random(),
				title = title,
				recordedAt = Clock.System.now(),
				duration = Duration.ZERO,
			)
			runCatching { diaryClient.createEntry(entry, bytes) }
				.onFailure { e -> _uiState.update { it.copy(error = e.message) } }
			_uiState.update { it.copy(pendingRecording = null, pendingTitle = "") }
		}
	}

	fun deleteEntry(entry: VoiceDiaryEntry) {
		viewModelScope.launch {
			runCatching { diaryClient.deleteEntry(entry.id) }
				.onFailure { e -> _uiState.update { it.copy(error = e.message) } }
		}
	}

	fun transcribe(entry: VoiceDiaryEntry) {
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
				_uiState.update { it.copy(error = e.message) }
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
	val entries: List<VoiceDiaryEntry> = emptyList(),
	val isRecording: Boolean = false,
	val pendingRecording: Recording? = null,
	val pendingTitle: String = "",
	val error: String? = null,
	val canTranscribe: Boolean = false,
	val recorderAvailable: Boolean = true,
)

@Stable
data class Recording(val data: ByteArray)
