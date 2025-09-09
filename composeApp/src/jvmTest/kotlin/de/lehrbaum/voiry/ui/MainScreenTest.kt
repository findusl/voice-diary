package de.lehrbaum.voiry.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import de.lehrbaum.voiry.UiTest
import de.lehrbaum.voiry.api.v1.DiaryClient
import de.lehrbaum.voiry.api.v1.TranscriptionStatus
import de.lehrbaum.voiry.api.v1.VoiceDiaryEntry
import de.lehrbaum.voiry.audio.Recorder
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import io.ktor.client.HttpClient
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
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
			val client =
				FakeDiaryClient(
					connectionErrors = mutableListOf(
						"Connection refused",
						"Still no connection",
					),
				)
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

			waitForIdle()

			onNodeWithText("Error: Connection refused").assertIsDisplayed()

			client.retry()

			waitUntil { onNodeWithText("Error: Still no connection").isDisplayed() }
			onAllNodesWithText("Error: Connection refused", useUnmergedTree = true).assertCountEquals(0)

			client.retry()

			waitUntil {
				onAllNodesWithText("Error: Still no connection", useUnmergedTree = true)
					.fetchSemanticsNodes(atLeastOneRootRequired = false)
					.isEmpty()
			}
		}

	@Test
	fun displays_seeded_recordings_and_title_and_hides_fab_when_unavailable() =
		runComposeUiTest {
			val client = FakeDiaryClient()
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

			waitForIdle()

			// Title present
			onNodeWithText("Voice Diary").assertIsDisplayed()

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
			waitForIdle()
			onAllNodesWithText("Audio recorder not available on this platform/device.").assertCountEquals(0)
		}

	@Test
	fun press_record_then_stop_adds_new_item_and_toggles_label() =
		runComposeUiTest {
			val client = FakeDiaryClient()
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
			waitForIdle()
			onNodeWithText("Stop").assertIsDisplayed()

			// Stop recording and confirm dialog
			onNodeWithText("Stop").performClick()
			waitForIdle()
			onNodeWithText("Title").performTextInput("My Entry")
			onNodeWithText("Save").performClick()
			waitForIdle()
			onNodeWithText("Record").assertIsDisplayed()
			// New item appears at top with expected title and transcript
			onNodeWithText("My Entry").assertIsDisplayed()
			onNodeWithText("Transcript for My Entry").assertIsDisplayed()
		}

	@Test
	fun delete_removes_item_from_list() =
		runComposeUiTest {
			val client = FakeDiaryClient()
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

			waitForIdle()

			onNodeWithText("Recording 1").assertIsDisplayed()
			onAllNodesWithText("Delete")[0].performClick()
			waitForIdle()
			onAllNodesWithText("Recording 1").assertCountEquals(0)
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
			val client = FakeDiaryClient(initial = listOf(entry))
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

			waitForIdle()

			onNodeWithText("Recording 1").assertIsDisplayed()
			onNodeWithText("Not yet transcribed").assertIsDisplayed()
		}
}

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
private class FakeDiaryClient(
	initial: List<VoiceDiaryEntry> = List(3) { idx ->
		VoiceDiaryEntry(
			id = Uuid.random(),
			title = "Recording ${idx + 1}",
			recordedAt = Clock.System.now(),
			duration = Duration.ZERO,
			transcriptionText = "Transcript ${idx + 1}",
			transcriptionStatus = TranscriptionStatus.DONE,
		)
	},
	connectionErrors: MutableList<String> = mutableListOf(),
) : DiaryClient(baseUrl = "", httpClient = HttpClient()) {
	private val pendingErrors = ArrayDeque(connectionErrors)

	init {
		connectionErrorState.value = pendingErrors.removeFirstOrNull()
	}

	fun retry() {
		connectionErrorState.value = pendingErrors.removeFirstOrNull()
	}

	private val _entries = MutableStateFlow(initial)
	override val entries: MutableStateFlow<List<VoiceDiaryEntry>> get() = _entries

	override suspend fun createEntry(entry: VoiceDiaryEntry, audio: ByteArray): VoiceDiaryEntry {
		val withTranscript = entry.copy(
			transcriptionText = "Transcript for ${entry.title}",
			transcriptionStatus = TranscriptionStatus.DONE,
		)
		_entries.value = listOf(withTranscript) + _entries.value
		return withTranscript
	}

	override suspend fun deleteEntry(id: Uuid) {
		_entries.value = _entries.value.filterNot { it.id == id }
	}

	override fun close() {}
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
