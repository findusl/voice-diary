package de.lehrbaum.voiry.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import de.findusl.wavrecorder.Recorder
import de.lehrbaum.voiry.UiTest
import de.lehrbaum.voiry.api.v1.DiaryClient
import de.lehrbaum.voiry.api.v1.TranscriptionStatus
import de.lehrbaum.voiry.api.v1.VoiceDiaryEntry
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import kotlinx.io.Buffer
import kotlinx.io.writeString
import org.junit.Test
import org.junit.experimental.categories.Category

@OptIn(ExperimentalTestApi::class, ExperimentalTime::class, ExperimentalUuidApi::class)
@Category(UiTest::class)
class MainScreenTest {
	@Test
	fun error_banner_persists_across_retries_and_disappears_on_success() =
		runComposeUiTest {
			val clientMock = diaryClientMock(
				connectionErrors = listOf(
					"Connection refused",
					"Still no connection",
				),
			)
			val client = clientMock.client
			val recorder = mock<Recorder>()
			every { recorder.isAvailable } returns true

			setContent {
				CompositionLocalProvider(
					LocalLifecycleOwner provides FakeLifecycleOwner(),
					LocalViewModelStoreOwner provides FakeViewModelStoreOwner(),
				) {
					MaterialTheme {
						MainScreen(
							diaryClient = client,
							recorder = recorder,
							transcriber = null,
							onEntryClick = { },
						)
					}
				}
			}

			waitUntilAtLeastOneExists(hasText("Error: Connection refused"))

			clientMock.retry()

			waitUntilAtLeastOneExists(hasText("Error: Still no connection"))
			onAllNodesWithText("Error: Connection refused").assertCountEquals(0)

			clientMock.retry()

			waitUntilDoesNotExist(hasText("Error: Still no connection"))
		}

	@Test
	fun permission_error_shows_grant_button_and_triggers_callback() =
		runComposeUiTest {
			val clientMock = diaryClientMock(connectionErrors = listOf("Permission missing"))
			val client = clientMock.client
			val recorder = mock<Recorder>()
			every { recorder.isAvailable } returns true
			var permissionRequested = false

			setContent {
				CompositionLocalProvider(
					LocalLifecycleOwner provides FakeLifecycleOwner(),
					LocalViewModelStoreOwner provides FakeViewModelStoreOwner(),
				) {
					MaterialTheme {
						MainScreen(
							diaryClient = client,
							recorder = recorder,
							transcriber = null,
							onEntryClick = { },
							onRequestAudioPermission = { permissionRequested = true },
						)
					}
				}
			}

			waitUntilAtLeastOneExists(hasText("Grant permission"))
			onNodeWithText("Grant permission").performClick()
			assert(permissionRequested)
		}

	@Test
	fun displays_seeded_recordings_and_title_and_hides_fab_when_unavailable() =
		runComposeUiTest {
			val client = diaryClientMock().client
			val unavailableRecorder = mock<Recorder>()
			every { unavailableRecorder.isAvailable } returns false

			setContent {
				CompositionLocalProvider(
					LocalLifecycleOwner provides FakeLifecycleOwner(),
					LocalViewModelStoreOwner provides FakeViewModelStoreOwner(),
				) {
					MaterialTheme {
						MainScreen(
							diaryClient = client,
							recorder = unavailableRecorder,
							transcriber = null,
							onEntryClick = { },
						)
					}
				}
			}

			waitUntilAtLeastOneExists(hasText("Voice Diary")) // Title present

			// The list contains three predefined recordings with transcripts
			onNodeWithText("Recording 1").assertIsDisplayed()
			onNodeWithText("Transcript 1").assertIsDisplayed()
			onNodeWithText("Recording 2").assertIsDisplayed()
			onNodeWithText("Transcript 2").assertIsDisplayed()
			onNodeWithText("Recording 3").assertIsDisplayed()
			onNodeWithText("Transcript 3").assertIsDisplayed()

			// FAB should be hidden when recorder is not available
			onAllNodesWithText("Record").assertCountEquals(0)
			// Info banner can be dismissed
			onNodeWithText("Audio recorder not available on this platform/device.").assertIsDisplayed()
			onNodeWithText("Dismiss").performClick()
			waitUntilDoesNotExist(
				hasText("Audio recorder not available on this platform/device."),
			)
		}

	@Test
	fun press_record_then_stop_adds_new_item_and_toggles_label() =
		runComposeUiTest {
			val clientMock = diaryClientMock()
			val client = clientMock.client
			val buffer = Buffer().apply { writeString("new bytes") }
			val recorder = mock<Recorder>()
			every { recorder.isAvailable } returns true
			every { recorder.startRecording() } returns Unit
			every { recorder.stopRecording() } returns Result.success(buffer)

			setContent {
				CompositionLocalProvider(
					LocalLifecycleOwner provides FakeLifecycleOwner(),
					LocalViewModelStoreOwner provides FakeViewModelStoreOwner(),
				) {
					MaterialTheme {
						MainScreen(
							diaryClient = client,
							recorder = recorder,
							transcriber = null,
							onEntryClick = { },
						)
					}
				}
			}

			// Initially we should see Record
			onNodeWithText("Record").assertIsDisplayed()

			// Start recording
			onNodeWithText("Record").performClick()
			waitUntilAtLeastOneExists(hasText("Stop"))

			// Stop recording and confirm dialog
			onNodeWithText("Stop").performClick()
			waitUntilAtLeastOneExists(hasText("Title"))
			onNodeWithText("Title").performTextInput("My Entry")
			onNodeWithText("Save").performClick()
			waitUntilAtLeastOneExists(hasText("My Entry"))
			onNodeWithText("Record").assertIsDisplayed()
			// New item appears at top with expected title and transcript
			onNodeWithText("Transcript for My Entry").assertIsDisplayed()
		}

	@Test
	fun changing_recording_date_updates_saved_entry() =
		runComposeUiTest {
			val timeZone = TimeZone.currentSystemDefault()
			val dateFormat = LocalDate.Format { date(LocalDate.Formats.ISO) }
			var savedEntry: VoiceDiaryEntry? = null
			val clientMock = diaryClientMock(onCreateEntry = { savedEntry = it })
			val client = clientMock.client
			val buffer = Buffer().apply { writeString("new bytes") }
			val recorder = mock<Recorder>()
			every { recorder.isAvailable } returns true
			every { recorder.startRecording() } returns Unit
			every { recorder.stopRecording() } returns Result.success(buffer)
			val viewModel = MainViewModel(
				diaryClient = client,
				recorder = recorder,
				transcriber = null,
			)

			setContent {
				CompositionLocalProvider(
					LocalLifecycleOwner provides FakeLifecycleOwner(),
					LocalViewModelStoreOwner provides FakeViewModelStoreOwner(),
				) {
					MaterialTheme {
						MainScreen(
							viewModel = viewModel,
							transcriber = null,
							onEntryClick = { },
						)
					}
				}
			}

			onNodeWithText("Record").performClick()
			waitUntilAtLeastOneExists(hasText("Stop"))

			onNodeWithText("Stop").performClick()
			waitUntilAtLeastOneExists(hasText("Title"))

			onNodeWithTag("saveRecordingDateButton").assertIsDisplayed()

			val initialDate = Clock.System.now().toLocalDateTime(timeZone).date
			val desiredDay = if (initialDate.day != 15) 15 else 16
			val desiredDate = LocalDate(initialDate.year, initialDate.month, desiredDay)
			val desiredDateText = desiredDate.format(dateFormat)
			val desiredDateMillis = desiredDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

			viewModel.updatePendingRecordedDate(desiredDateMillis)

			waitUntilAtLeastOneExists(hasText(desiredDateText))

			onNodeWithText("Title").performTextInput("Backdated entry")
			onNodeWithText("Save").performClick()

			waitUntilAtLeastOneExists(hasText("Backdated entry"))
			val savedDate = checkNotNull(savedEntry).recordedAt.toLocalDateTime(timeZone).date
			assertEquals(desiredDate, savedDate)
		}

	@Test
	fun delete_removes_item_from_list() =
		runComposeUiTest {
			val clientMock = diaryClientMock()
			val client = clientMock.client
			val recorder = mock<Recorder>()
			every { recorder.isAvailable } returns false

			setContent {
				CompositionLocalProvider(
					LocalLifecycleOwner provides FakeLifecycleOwner(),
					LocalViewModelStoreOwner provides FakeViewModelStoreOwner(),
				) {
					MaterialTheme {
						MainScreen(
							diaryClient = client,
							recorder = recorder,
							transcriber = null,
							onEntryClick = { },
						)
					}
				}
			}

			waitUntilAtLeastOneExists(hasText("Recording 1"))

			onAllNodesWithText("Delete")[0].performClick()
			waitUntilAtLeastOneExists(hasText("Delete entry?"))
			onAllNodesWithText("Delete")[3].performClick()
			waitUntilDoesNotExist(hasText("Recording 1"))
		}

	@Test
	fun shows_placeholder_when_transcription_missing() =
		runComposeUiTest {
			val entry = VoiceDiaryEntry(
				id = Uuid.random(),
				title = "Recording 1",
				recordedAt = Clock.System.now(),
				duration = Duration.ZERO,
				transcriptionText = null,
				transcriptionStatus = TranscriptionStatus.NONE,
			)
			val client = diaryClientMock(initial = listOf(entry).toPersistentList()).client
			val recorder = mock<Recorder>()
			every { recorder.isAvailable } returns false

			setContent {
				CompositionLocalProvider(
					LocalLifecycleOwner provides FakeLifecycleOwner(),
					LocalViewModelStoreOwner provides FakeViewModelStoreOwner(),
				) {
					MaterialTheme {
						MainScreen(
							diaryClient = client,
							recorder = recorder,
							transcriber = null,
							onEntryClick = { },
						)
					}
				}
			}

			waitUntilAtLeastOneExists(hasText("Recording 1"))

			onNodeWithText("Not yet transcribed").assertIsDisplayed()
		}
}

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
private data class DiaryClientMock(
	val client: DiaryClient,
	val entries: MutableStateFlow<PersistentList<VoiceDiaryEntry>>,
	val connectionError: MutableStateFlow<String?>,
	private val pendingErrors: ArrayDeque<String>,
) {
	fun retry() {
		connectionError.value = pendingErrors.removeFirstOrNull()
	}
}

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
private fun diaryClientMock(
	initial: PersistentList<VoiceDiaryEntry> = List(3) { idx ->
		VoiceDiaryEntry(
			id = Uuid.random(),
			title = "Recording ${idx + 1}",
			recordedAt = Clock.System.now(),
			duration = Duration.ZERO,
			transcriptionText = "Transcript ${idx + 1}",
			transcriptionStatus = TranscriptionStatus.DONE,
		)
	}.toPersistentList(),
	connectionErrors: List<String> = emptyList(),
	onCreateEntry: ((VoiceDiaryEntry) -> Unit)? = null,
): DiaryClientMock {
	val pendingErrors = ArrayDeque(connectionErrors)
	val connectionErrorFlow = MutableStateFlow(pendingErrors.removeFirstOrNull())
	val entriesFlow = MutableStateFlow(initial)
	val client = mock<DiaryClient> {
		every { entries } returns entriesFlow
		every { connectionError } returns connectionErrorFlow
		everySuspend { createEntry(any(), any()) } calls { (entry: VoiceDiaryEntry, _: ByteArray) ->
			onCreateEntry?.invoke(entry)
			val withTranscript = entry.copy(
				transcriptionText = "Transcript for ${entry.title}",
				transcriptionStatus = TranscriptionStatus.DONE,
			)
			entriesFlow.value = entriesFlow.value.add(0, withTranscript)
			withTranscript
		}
		everySuspend { deleteEntry(any()) } calls { (id: Uuid) ->
			entriesFlow.value = entriesFlow.value.filterNot { it.id == id }.toPersistentList()
		}
		every { entryFlow(any()) } calls { (id: Uuid) ->
			MutableStateFlow(entriesFlow.value.firstOrNull { it.id == id })
		}
		everySuspend { getAudio(any()) } returns byteArrayOf(0)
	}
	return DiaryClientMock(client, entriesFlow, connectionErrorFlow, pendingErrors)
}

private class FakeLifecycleOwner : LifecycleOwner {
	private val registry = LifecycleRegistry(this).apply {
		currentState = Lifecycle.State.RESUMED
	}
	override val lifecycle: Lifecycle get() = registry
}

private class FakeViewModelStoreOwner : ViewModelStoreOwner {
	override val viewModelStore: ViewModelStore = ViewModelStore()
}
