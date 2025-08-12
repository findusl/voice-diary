package de.lehrbaum.voiry.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import de.lehrbaum.voiry.recordings.Recording
import de.lehrbaum.voiry.recordings.RecordingRepository
import kotlinx.io.Buffer
import kotlinx.io.writeString
import org.junit.Test
import androidx.compose.ui.test.ExperimentalTestApi

@OptIn(ExperimentalTestApi::class)
class MainScreenTest {

    @Test
    fun displays_seeded_recordings_and_title() = runComposeUiTest {
        val repo = FakeRecordingRepository()

        setContent {
            MaterialTheme {
                MainScreen(repository = repo, enableRecording = false)
            }
        }

        waitForIdle()

        // Title present
        onNodeWithText("Voice Diary", substring = false).assertIsDisplayed()

        // The list contains three predefined recordings
        onNodeWithText("Recording 1", substring = false).assertIsDisplayed()
        onNodeWithText("Recording 2", substring = false).assertIsDisplayed()
        onNodeWithText("Recording 3", substring = false).assertIsDisplayed()

        // Record button is visible in idle state
        onNodeWithText("Record", substring = false).assertIsDisplayed()
    }
}

private class FakeRecordingRepository : RecordingRepository {
    private val items: List<Recording> = List(3) { idx ->
        val buf = Buffer().apply { writeString("Dummy #${idx + 1}") }
        Recording(id = "id-${idx + 1}", title = "Recording ${idx + 1}", bytes = buf)
    }

    override suspend fun listRecordings(): List<Recording> = items

    override suspend fun saveRecording(bytes: Buffer): Recording {
        return Recording(id = "id-new", title = "New Recording", bytes = bytes)
    }
}
