package de.lehrbaum.voiry.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.lehrbaum.voiry.api.v1.DiaryClient
import de.lehrbaum.voiry.api.v1.TranscriptionStatus
import de.lehrbaum.voiry.api.v1.UpdateTranscriptionRequest
import de.lehrbaum.voiry.api.v1.VoiceDiaryEntry
import de.lehrbaum.voiry.audio.Player
import de.lehrbaum.voiry.audio.Transcriber
import de.lehrbaum.voiry.audio.platformPlayer
import de.lehrbaum.voiry.audio.platformTranscriber
import de.lehrbaum.voiry.runSuspendCatching
import java.io.Closeable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.io.Buffer

@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
class EntryDetailViewModel(
	private val diaryClient: DiaryClient,
	private val entryId: Uuid,
	private val player: Player = platformPlayer,
	val transcriber: Transcriber? = platformTranscriber,
) : ViewModel(), Closeable {
	private val _uiState = MutableStateFlow(EntryDetailUiState())
	val uiState: StateFlow<EntryDetailUiState> = _uiState.asStateFlow()

	init {
		viewModelScope.launch {
			diaryClient.entryFlow(entryId).collect { entry ->
				_uiState.update {
					it.copy(
						entry = entry,
						editedText = if (!it.isEditing) entry?.transcriptionText ?: "" else it.editedText,
					)
				}
			}
		}
	}

	override fun onCleared() {
		super.onCleared()
		close()
	}

	override fun close() {
		runCatching { player.close() }
	}

	fun togglePlayback() {
		val audio = _uiState.value.audio
		if (audio == null) {
			downloadAudio { data ->
				player.play(data)
				_uiState.update { state -> state.copy(isPlaying = true) }
			}
			return
		}
		if (_uiState.value.isPlaying) {
			player.stop()
		} else {
			player.play(audio)
		}
		_uiState.update { it.copy(isPlaying = !it.isPlaying) }
	}

	fun startEditing() {
		_uiState.update { state ->
			state.copy(isEditing = true, editedText = state.entry?.transcriptionText ?: "")
		}
	}

	fun updateEditedText(text: String) {
		_uiState.update { it.copy(editedText = text) }
	}

	fun cancelEdit() {
		_uiState.update { state ->
			state.copy(isEditing = false, editedText = state.entry?.transcriptionText ?: "")
		}
	}

	fun saveEdit() {
		val entry = _uiState.value.entry ?: return
		val edited = _uiState.value.editedText
		viewModelScope.launch {
			_uiState.update { it.copy(isSaving = true) }
			runSuspendCatching {
				diaryClient.updateTranscription(
					entry.id,
					UpdateTranscriptionRequest(
						edited,
						TranscriptionStatus.DONE,
						Clock.System.now(),
					),
				)
			}.onSuccess {
				_uiState.update { it.copy(isSaving = false, isEditing = false) }
			}.onFailure { e ->
				_uiState.update { it.copy(isSaving = false, error = e.message) }
			}
		}
	}

	fun transcribe() {
		val t = transcriber
		if (t == null) {
			_uiState.update { it.copy(error = "Transcriber unavailable") }
			return
		}
		val audio = _uiState.value.audio
		if (audio == null) {
			downloadAudio { data ->
				viewModelScope.launch {
					transcribeEntry(diaryClient, t, entryId, data)
						.onFailure { e ->
							_uiState.update { it.copy(error = e.message) }
						}
				}
			}
		} else {
			viewModelScope.launch {
				transcribeEntry(diaryClient, t, entryId, audio)
					.onFailure { e -> _uiState.update { it.copy(error = e.message) } }
			}
		}
	}

	private fun downloadAudio(onSuccess: (ByteArray) -> Unit) {
		viewModelScope.launch {
			runSuspendCatching { diaryClient.getAudio(entryId) }
				.onSuccess { data ->
					_uiState.update { it.copy(audio = data) }
					onSuccess(data)
				}.onFailure { e -> _uiState.update { it.copy(error = e.message) } }
		}
	}

	fun delete(onSuccess: () -> Unit) {
		viewModelScope.launch {
			runSuspendCatching { diaryClient.deleteEntry(entryId) }
				.onSuccess { onSuccess() }
				.onFailure { e -> _uiState.update { it.copy(error = e.message) } }
		}
	}
}

@Immutable
data class EntryDetailUiState(
	val entry: VoiceDiaryEntry? = null,
	/**
	 * Audio data must not be mutated; the ByteArray is treated as immutable by convention.
	 */
	val audio: ByteArray? = null,
	val isPlaying: Boolean = false,
	val error: String? = null,
	val isEditing: Boolean = false,
	val editedText: String = "",
	val isSaving: Boolean = false,
)

@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
private suspend fun transcribeEntry(
	diaryClient: DiaryClient,
	transcriber: Transcriber,
	entryId: Uuid,
	audio: ByteArray,
): Result<Unit> =
	runSuspendCatching {
		val buffer = Buffer().apply { write(audio) }
		val text = transcriber.transcribe(buffer)
		diaryClient.updateTranscription(
			entryId,
			UpdateTranscriptionRequest(
				text,
				TranscriptionStatus.DONE,
				Clock.System.now(),
			),
		)
	}
