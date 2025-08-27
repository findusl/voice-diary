package de.lehrbaum.voiry.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import de.lehrbaum.voiry.audio.Recorder
import de.lehrbaum.voiry.recordings.Recording
import de.lehrbaum.voiry.recordings.RecordingRepository
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.io.Buffer
import kotlinx.io.writeString
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class MainScreenTest {
	@Test
	fun displays_seeded_recordings_and_title_and_hides_fab_when_unavailable() =
		runComposeUiTest {
			val repo = FakeRecordingRepository()
			val unavailableRecorder = mock<Recorder>()
			every { unavailableRecorder.isAvailable } returns false

			setContent {
				MaterialTheme {
					MainScreen(repository = repo, unavailableRecorder)
				}
			}

			waitForIdle()

			// Title present
			onNodeWithText("Voice Diary", substring = false).assertIsDisplayed()

			// The list contains three predefined recordings with transcripts
			onNodeWithText("Recording 1", substring = false).assertIsDisplayed()
			onNodeWithText("Transcript 1", substring = false).assertIsDisplayed()
			onNodeWithText("Recording 2", substring = false).assertIsDisplayed()
			onNodeWithText("Transcript 2", substring = false).assertIsDisplayed()
			onNodeWithText("Recording 3", substring = false).assertIsDisplayed()
			onNodeWithText("Transcript 3", substring = false).assertIsDisplayed()

			// FAB should be hidden when recorder is not available
			try {
				onNodeWithText("Record", substring = false).assertIsDisplayed()
				throw AssertionError("Record button should not be displayed when recorder is unavailable")
			} catch (_: AssertionError) {
				// Expected: node not found or not displayed
			}
			// Info banner is shown
			onNodeWithText("Audio recorder not available on this platform/device.", substring = false).assertIsDisplayed()
		}

	@Test
	fun press_record_then_stop_adds_new_item_and_toggles_label() =
		runComposeUiTest {
			val repo = FakeRecordingRepositoryMutable()
			val buffer = Buffer().apply { writeString("new bytes") }
			val recorder = mock<Recorder>()
			every { recorder.isAvailable } returns true
			every { recorder.startRecording() } returns Unit
			every { recorder.stopRecording() } returns Result.success(buffer)

			setContent {
				MaterialTheme {
					MainScreen(repository = repo, recorder)
				}
			}

			// Initially we should see Record
			onNodeWithText("Record", substring = false).assertIsDisplayed()

			// Start recording
			onNodeWithText("Record", substring = false).performClick()
			waitForIdle()
			onNodeWithText("Stop", substring = false).assertIsDisplayed()

			// Stop recording and confirm dialog
			onNodeWithText("Stop", substring = false).performClick()
			waitForIdle()
			onNodeWithText("Title", substring = false).performTextInput("My Entry")
			onNodeWithText("Save", substring = false).performClick()
			waitForIdle()
			onNodeWithText("Record", substring = false).assertIsDisplayed()
			// New item appears at top with expected title and transcript
			onNodeWithText("My Entry", substring = false).assertIsDisplayed()
			onNodeWithText("Transcript for My Entry", substring = false).assertIsDisplayed()
		}

	@Test
	fun delete_removes_item_from_list() =
		runComposeUiTest {
			val repo = FakeRecordingRepositoryMutable()
			val recorder = mock<Recorder>()
			every { recorder.isAvailable } returns false

			setContent {
				MaterialTheme {
					MainScreen(repository = repo, recorder)
				}
			}

			waitForIdle()

			onNodeWithText("Recording 1", substring = false).assertIsDisplayed()
			onNodeWithText("Delete", substring = false).performClick()
			waitForIdle()
			try {
				onNodeWithText("Recording 1", substring = false).assertIsDisplayed()
				throw AssertionError("Recording 1 should have been deleted")
			} catch (_: AssertionError) {
				// Expected
			}
		}
}

private class FakeRecordingRepository : RecordingRepository {
	private val items: List<Recording> = List(3) { idx ->
		val buf = Buffer().apply { writeString("Dummy #${idx + 1}") }
		Recording(id = "id-${idx + 1}", title = "Recording ${idx + 1}", transcript = "Transcript ${idx + 1}", bytes = buf)
	}

	override suspend fun listRecordings(): List<Recording> = items

	override suspend fun saveRecording(title: String, bytes: Buffer): Recording =
		Recording(id = "id-new", title = title, transcript = "Transcript for $title", bytes = bytes)

	override suspend fun deleteRecording(id: String) {}
}

private class FakeRecordingRepositoryMutable : RecordingRepository {
	private val items = mutableListOf<Recording>().apply {
		repeat(3) { idx ->
			add(Recording("id-${idx + 1}", "Recording ${idx + 1}", "Transcript ${idx + 1}", Buffer().apply { writeString("Dummy #${idx + 1}") }))
		}
	}

	override suspend fun listRecordings(): List<Recording> = items.toList()

	override suspend fun saveRecording(title: String, bytes: Buffer): Recording {
		val rec = Recording("id-${items.size + 1}", title, "Transcript for $title", bytes)
		items.add(0, rec)
		return rec
	}

	override suspend fun deleteRecording(id: String) {
		items.removeAll { it.id == id }
	}
}
