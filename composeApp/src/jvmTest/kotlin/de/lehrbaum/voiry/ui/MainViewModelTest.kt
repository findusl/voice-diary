package de.lehrbaum.voiry.ui

import de.lehrbaum.voicerecorder.Recorder
import de.lehrbaum.voiry.api.v1.DiaryClient
import de.lehrbaum.voiry.api.v1.VoiceDiaryEntry
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
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

@OptIn(ExperimentalUuidApi::class)
private fun createViewModel(): MainViewModel {
	val diaryClient = mock<DiaryClient>()
	every { diaryClient.connectionError } returns MutableStateFlow<String?>(null)
	every { diaryClient.entries } returns MutableStateFlow(persistentListOf<VoiceDiaryEntry>())

	val recorder = mock<Recorder>(mode = MockMode.autoUnit)
	every { recorder.isAvailable } returns true

	return MainViewModel(
		diaryClient = diaryClient,
		recorder = recorder,
		transcriber = null,
	)
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
