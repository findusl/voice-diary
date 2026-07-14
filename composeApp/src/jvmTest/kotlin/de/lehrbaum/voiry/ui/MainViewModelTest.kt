package de.lehrbaum.voiry.ui

import de.findusl.wavrecorder.Recorder
import de.lehrbaum.voiry.api.v1.DiaryClient
import de.lehrbaum.voiry.api.v1.VoiceDiaryEntry
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.TestTimeSource
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.io.Buffer
import kotlinx.io.writeString
import org.junit.Test

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
class MainViewModelTest {
	@Test
	fun saveRecording_failure_preserves_pending_recording_metadata_and_audio() =
		runTest {
			val diaryClient = emptyDiaryClient()
			var failUpload = true
			val attemptedEntries = mutableListOf<VoiceDiaryEntry>()
			everySuspend { diaryClient.createEntry(any(), any()) } calls { (entry: VoiceDiaryEntry, _: ByteArray) ->
				attemptedEntries += entry
				if (failUpload) throw IllegalStateException("Upload failed")
				entry
			}
			val recorder = successfulRecorder("recorded bytes")
			val timeSource = TestTimeSource()
			val viewModel = MainViewModel(
				diaryClient = diaryClient,
				recorder = recorder,
				transcriber = null,
				timeSource = timeSource,
			)
			try {
				viewModel.startRecording()
				timeSource += 3.seconds
				viewModel.stopRecording()
				val recording = assertNotNull(viewModel.uiState.first { it.pendingRecording != null }.pendingRecording)
				val recordedAt = LocalDateTime(2025, 2, 3, 4, 5).toInstant(TimeZone.UTC)
				viewModel.updatePendingTitle("Keep me")
				viewModel.updatePendingRecordedAt(recordedAt)

				viewModel.saveRecording()

				val failedState = viewModel.uiState.first { it.error != null }
				assertEquals("Upload failed", failedState.error)
				assertEquals("Keep me", failedState.pendingTitle)
				assertEquals(recordedAt, failedState.pendingRecordedAt)
				assertEquals(recording, failedState.pendingRecording)
				assertEquals(3.seconds, failedState.pendingRecording?.duration)
				assertContentEquals("recorded bytes".encodeToByteArray(), failedState.pendingRecording?.data)

				failUpload = false
				viewModel.saveRecording()
				val savedState = viewModel.uiState.first { it.pendingRecording == null }
				assertFalse(savedState.isSaving)
				assertEquals(null, savedState.error)
				assertEquals(2, attemptedEntries.size)
				assertEquals(attemptedEntries.first().id, attemptedEntries.last().id)
			} finally {
				viewModel.close()
			}
		}

	@Test
	fun saveRecording_ignores_duplicate_submission_while_upload_is_in_progress() =
		runTest {
			val diaryClient = emptyDiaryClient()
			val uploadStarted = CompletableDeferred<Unit>()
			val releaseUpload = CompletableDeferred<Unit>()
			var createCalls = 0
			everySuspend { diaryClient.createEntry(any(), any()) } calls { (entry: VoiceDiaryEntry, _: ByteArray) ->
				createCalls += 1
				uploadStarted.complete(Unit)
				releaseUpload.await()
				entry
			}
			val viewModel = MainViewModel(
				diaryClient = diaryClient,
				recorder = successfulRecorder("audio"),
				transcriber = null,
			)
			try {
				viewModel.startRecording()
				viewModel.stopRecording()
				viewModel.uiState.first { it.pendingRecording != null }
				viewModel.updatePendingTitle("Only once")

				viewModel.saveRecording()
				uploadStarted.await()
				assertTrue(viewModel.uiState.first { it.isSaving }.isSaving)

				viewModel.saveRecording()
				assertEquals(1, createCalls)

				releaseUpload.complete(Unit)
				val savedState = viewModel.uiState.first { it.pendingRecording == null }
				assertFalse(savedState.isSaving)
			} finally {
				viewModel.close()
			}
		}

	@Test
	fun saveRecording_uses_monotonic_recording_duration() =
		runTest {
			val diaryClient = emptyDiaryClient()
			var createdEntry: VoiceDiaryEntry? = null
			everySuspend { diaryClient.createEntry(any(), any()) } calls { (entry: VoiceDiaryEntry, _: ByteArray) ->
				createdEntry = entry
				entry
			}
			val timeSource = TestTimeSource()
			val viewModel = MainViewModel(
				diaryClient = diaryClient,
				recorder = successfulRecorder("audio"),
				transcriber = null,
				timeSource = timeSource,
			)
			try {
				viewModel.startRecording()
				timeSource += 7.seconds
				viewModel.stopRecording()
				val pendingState = viewModel.uiState.first { it.pendingRecording != null }
				assertEquals(7.seconds, pendingState.pendingRecording?.duration)

				viewModel.saveRecording()
				viewModel.uiState.first { it.pendingRecording == null }

				assertEquals(7.seconds, assertNotNull(createdEntry).duration)
			} finally {
				viewModel.close()
			}
		}

	@Test
	fun updatePendingRecordedDate_preserves_time_component_in_local_timezone() =
		withTimeZone("America/Los_Angeles") { zone ->
			runTest {
				val viewModel = createViewModel()
				try {
					val initialDateTime = LocalDateTime(2024, 7, 21, 22, 30)
					viewModel.updatePendingRecordedAt(initialDateTime.toInstant(zone))

					val selectedDate = LocalDate(2024, 7, 18)
					val selectedDateMillis =
						selectedDate
							.atStartOfDayIn(TimeZone.UTC)
							.toEpochMilliseconds()
					val expectedInstant =
						LocalDateTime(selectedDate, initialDateTime.time).toInstant(zone)

					viewModel.updatePendingRecordedDate(selectedDateMillis)

					val updatedState =
						viewModel.uiState.first { it.pendingRecordedAt == expectedInstant }
					val updatedDateTime = updatedState.pendingRecordedAt.toLocalDateTime(zone)

					assertEquals(selectedDate, updatedDateTime.date)
					assertEquals(initialDateTime.time, updatedDateTime.time)
					assertEquals(expectedInstant, updatedState.pendingRecordedAt)
				} finally {
					viewModel.close()
				}
			}
		}

	@Test
	fun updatePendingRecordedTime_updates_time_without_changing_date() =
		withTimeZone("Asia/Tokyo") { zone ->
			runTest {
				val viewModel = createViewModel()
				try {
					val initialDateTime = LocalDateTime(2024, 12, 31, 6, 45)
					viewModel.updatePendingRecordedAt(initialDateTime.toInstant(zone))
					val expectedInstant =
						LocalDateTime(initialDateTime.date, LocalTime(1, 5)).toInstant(zone)

					viewModel.updatePendingRecordedTime(hour = 1, minute = 5)

					val updatedState =
						viewModel.uiState.first { it.pendingRecordedAt == expectedInstant }
					val updatedDateTime = updatedState.pendingRecordedAt.toLocalDateTime(zone)

					assertEquals(initialDateTime.date, updatedDateTime.date)
					assertEquals(LocalTime(1, 5), updatedDateTime.time)
					assertEquals(expectedInstant, updatedState.pendingRecordedAt)
				} finally {
					viewModel.close()
				}
			}
		}
}

@OptIn(ExperimentalUuidApi::class)
private fun createViewModel(): MainViewModel {
	val diaryClient = emptyDiaryClient()

	val recorder = mock<Recorder>(mode = MockMode.autoUnit)
	every { recorder.isAvailable } returns true

	return MainViewModel(
		diaryClient = diaryClient,
		recorder = recorder,
		transcriber = null,
	)
}

@OptIn(ExperimentalUuidApi::class)
private fun emptyDiaryClient(): DiaryClient =
	mock<DiaryClient> {
		every { connectionError } returns MutableStateFlow<String?>(null)
		every { entries } returns MutableStateFlow(persistentListOf<VoiceDiaryEntry>())
	}

private fun successfulRecorder(audio: String): Recorder {
	val recorder = mock<Recorder>(mode = MockMode.autoUnit)
	every { recorder.isAvailable } returns true
	every { recorder.startRecording() } returns Unit
	every { recorder.stopRecording() } returns Result.success(Buffer().apply { writeString(audio) })
	return recorder
}

private fun <T> withTimeZone(id: String, block: (TimeZone) -> T): T {
	val original = java.util.TimeZone.getDefault()
	val override = java.util.TimeZone.getTimeZone(id)
	java.util.TimeZone.setDefault(override)
	return try {
		block(TimeZone.of(id))
	} finally {
		java.util.TimeZone.setDefault(original)
	}
}
