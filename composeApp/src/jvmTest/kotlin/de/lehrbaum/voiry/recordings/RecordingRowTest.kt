package de.lehrbaum.voiry.recordings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import de.lehrbaum.voiry.UiTest
import de.lehrbaum.voiry.audio.Transcriber
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.io.Buffer
import kotlinx.io.writeString
import org.junit.experimental.categories.Category

@OptIn(ExperimentalTestApi::class)
@Category(UiTest::class)
class RecordingRowTest {
	@Test
	fun `transcribe button triggers repository`() =
		runComposeUiTest {
			val recording = Recording("id", "Title", "", Buffer().apply { writeString("data") })
			val repo = FakeDesktopRecordingRepository()
			val transcriber = object : Transcriber {
				override suspend fun transcribe(buffer: Buffer): String = "mock"
			}
			setContent {
				MaterialTheme {
					RecordingRow(recording, onDelete = {}, transcriber = transcriber, onTranscript = repo::transcribe)
				}
			}
			onNodeWithText("Transcribe", substring = false).performClick()
			waitForIdle()
			assertEquals(recording, repo.transcribed)
		}
}

private class FakeDesktopRecordingRepository {
	var transcribed: Recording? = null

	fun transcribe(recording: Recording, transcript: String) {
		transcribed = recording
	}
}
