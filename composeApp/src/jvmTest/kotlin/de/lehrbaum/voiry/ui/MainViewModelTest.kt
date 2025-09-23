package de.lehrbaum.voiry.ui

import de.lehrbaum.voiry.api.v1.DiaryClient
import de.lehrbaum.voiry.api.v1.UpdateTranscriptionRequest
import de.lehrbaum.voiry.api.v1.VoiceDiaryEntry
import de.lehrbaum.voiry.audio.Recorder
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.collections.immutable.persistentListOf
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
import org.junit.Test

@OptIn(ExperimentalTime::class)
class MainViewModelTest {
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

@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
private fun createViewModel(): MainViewModel =
	MainViewModel(
		diaryClient = FakeDiaryClient(),
		recorder = AvailableRecorder(),
		transcriber = null,
	)

private class AvailableRecorder : Recorder {
	override val isAvailable: Boolean = true

	override fun startRecording() = Unit

	override fun stopRecording(): Result<Buffer> = Result.failure(UnsupportedOperationException("Not used in tests"))

	override fun close() = Unit
}

@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
private class FakeDiaryClient : DiaryClient {
	override val connectionError = MutableStateFlow<String?>(null)
	override val entries = MutableStateFlow(persistentListOf<VoiceDiaryEntry>())

	override fun entryFlow(id: Uuid) = MutableStateFlow<VoiceDiaryEntry?>(null)

	override suspend fun createEntry(entry: VoiceDiaryEntry, audio: ByteArray) = entry

	override suspend fun updateTranscription(id: Uuid, request: UpdateTranscriptionRequest) = Unit

	override suspend fun deleteEntry(id: Uuid) = Unit

	override suspend fun getAudio(id: Uuid): ByteArray = ByteArray(0)

	override fun close() = Unit
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
