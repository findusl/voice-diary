package de.lehrbaum.voiry.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.lehrbaum.voicerecorder.Recorder
import de.lehrbaum.voicerecorder.platformRecorder
import de.lehrbaum.voiry.api.v1.DiaryClient
import de.lehrbaum.voiry.api.v1.TranscriptionStatus
import de.lehrbaum.voiry.api.v1.UpdateTranscriptionRequest
import de.lehrbaum.voiry.api.v1.VoiceDiaryEntry
import de.lehrbaum.voiry.audio.Transcriber
import de.lehrbaum.voiry.runSuspendCatching
import io.github.aakira.napier.Napier
import java.io.Closeable
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.io.Buffer
import kotlinx.io.readByteArray

@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
class MainViewModel(
	private val diaryClient: DiaryClient,
	private val recorder: Recorder = platformRecorder,
	private val transcriber: Transcriber?,
	cacheAvailable: Boolean = true,
) : ViewModel(), Closeable {
	private val timeZone = TimeZone.currentSystemDefault()
	private val baseState = MutableStateFlow(
		MainUiState(
			recorderAvailable = recorder.isAvailable,
			cacheUnavailable = !cacheAvailable,
		),
	)
	val uiState: StateFlow<MainUiState> =
		combine(
			baseState,
			diaryClient.entries.map { entries -> entries.map { it.toUi() }.toPersistentList() },
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
							pendingRecordedAt = Clock.System.now(),
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

	fun dismissCacheUnavailable() {
		baseState.update { it.copy(cacheUnavailableDismissed = true) }
	}

	fun updatePendingTitle(title: String) {
		baseState.update { it.copy(pendingTitle = title) }
	}

	fun updatePendingRecordedAt(recordedAt: Instant) {
		baseState.update { it.copy(pendingRecordedAt = recordedAt) }
	}

	fun updatePendingRecordedDate(selectedDateMillis: Long) {
		val selectedDate = Instant
			.fromEpochMilliseconds(selectedDateMillis)
			.toLocalDateTime(TimeZone.UTC)
			.date
		baseState.update { state ->
			val currentTime = state.pendingRecordedAt.toLocalDateTime(timeZone).time
			val updated = LocalDateTime(selectedDate, currentTime).toInstant(timeZone)
			state.copy(pendingRecordedAt = updated)
		}
	}

	fun updatePendingRecordedTime(hour: Int, minute: Int) {
		baseState.update { state ->
			val currentDate = state.pendingRecordedAt.toLocalDateTime(timeZone).date
			val updated = LocalDateTime(currentDate, LocalTime(hour, minute)).toInstant(timeZone)
			state.copy(pendingRecordedAt = updated)
		}
	}

	fun cancelSaveRecording() {
		baseState.update {
			it.copy(
				pendingRecording = null,
				pendingTitle = "",
				pendingRecordedAt = Clock.System.now(),
			)
		}
	}

	fun saveRecording() {
		val recording = baseState.value.pendingRecording ?: return
		val title = baseState.value.pendingTitle
		val recordedAt = baseState.value.pendingRecordedAt
		viewModelScope.launch {
			val bytes = recording.data
			val entry = VoiceDiaryEntry(
				id = Uuid.random(),
				title = title,
				recordedAt = recordedAt,
				duration = Duration.ZERO,
			)
			runSuspendCatching { diaryClient.createEntry(entry, bytes) }
				.onFailure { e -> baseState.update { it.copy(error = e.message) } }
			baseState.update {
				it.copy(
					pendingRecording = null,
					pendingTitle = "",
					pendingRecordedAt = Clock.System.now(),
				)
			}
		}
	}

	fun deleteEntry(entry: UiVoiceDiaryEntry) {
		viewModelScope.launch {
			runSuspendCatching { diaryClient.deleteEntry(entry.id) }
				.onFailure { e -> baseState.update { it.copy(error = e.message) } }
		}
	}

	fun transcribe(entry: UiVoiceDiaryEntry) {
		val transcriber = transcriber ?: return
		viewModelScope.launch {
			runSuspendCatching {
				val bytes = diaryClient.getAudio(entry.id)
				val buffer = Buffer().apply { write(bytes) }
				val prompt = initialPromptFromTitle(entry.title)
				val text = transcriber.transcribe(buffer, prompt)
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
				runSuspendCatching {
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

@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
@Immutable
data class MainUiState(
	val entries: PersistentList<UiVoiceDiaryEntry> = persistentListOf(),
	val isRecording: Boolean = false,
	val pendingRecording: Recording? = null,
	val pendingTitle: String = "",
	val pendingRecordedAt: Instant = Clock.System.now(),
	val error: String? = null,
	val recorderAvailable: Boolean = true,
	val recorderUnavailableDismissed: Boolean = false,
	val cacheUnavailable: Boolean = false,
	val cacheUnavailableDismissed: Boolean = false,
)

@Immutable
class Recording(val data: ByteArray)
