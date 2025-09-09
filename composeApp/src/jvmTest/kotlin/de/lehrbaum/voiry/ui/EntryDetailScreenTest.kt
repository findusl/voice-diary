package de.lehrbaum.voiry.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
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
import de.lehrbaum.voiry.UiTest
import de.lehrbaum.voiry.api.v1.DiaryClient
import de.lehrbaum.voiry.api.v1.TranscriptionStatus
import de.lehrbaum.voiry.api.v1.UpdateTranscriptionRequest
import de.lehrbaum.voiry.api.v1.VoiceDiaryEntry
import de.lehrbaum.voiry.audio.ModelDownloader
import de.lehrbaum.voiry.audio.Player
import de.lehrbaum.voiry.audio.Transcriber
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import dev.mokkery.verify
import io.ktor.client.HttpClient
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.io.Buffer
import org.junit.Test
import org.junit.experimental.categories.Category

@OptIn(ExperimentalTestApi::class, ExperimentalTime::class, ExperimentalUuidApi::class)
@Category(UiTest::class)
class EntryDetailScreenTest {
	@Test
	fun displays_entry_details_and_toggles_playback() =
		runComposeUiTest {
			val entry = VoiceDiaryEntry(
				id = Uuid.random(),
				title = "Recording 1",
				recordedAt = Clock.System.now(),
				duration = Duration.ZERO,
				transcriptionText = "Transcript 1",
				transcriptionStatus = TranscriptionStatus.DONE,
			)
			val audio = byteArrayOf(1)
			val client = EntryFakeDiaryClient(entry, audio)
			val player = mock<Player>(mode = MockMode.autoUnit)
			every { player.isAvailable } returns true

			setContent {
				CompositionLocalProvider(
					LocalLifecycleOwner provides EntryFakeLifecycleOwner(),
					LocalViewModelStoreOwner provides EntryFakeViewModelStoreOwner(),
				) {
					MaterialTheme {
						EntryDetailScreen(
							diaryClient = client,
							entryId = entry.id,
							onBack = {},
							player = player,
							transcriber = null,
						)
					}
				}
			}

			waitUntilAtLeastOneExists(hasText("Transcript 1"))

			onNodeWithText("Play").assertIsDisplayed()
			onNodeWithText("Play").performClick()
			waitUntilAtLeastOneExists(hasText("Stop"))
			verify { player.play(audio) }
			onNodeWithText("Stop").performClick()
			waitUntilAtLeastOneExists(hasText("Play"))
			verify { player.stop() }
		}

	@Test
	fun editing_transcription_calls_client_and_updates_ui() =
		runComposeUiTest {
			val entry = VoiceDiaryEntry(
				id = Uuid.random(),
				title = "Recording 1",
				recordedAt = Clock.System.now(),
				duration = Duration.ZERO,
				transcriptionText = "Transcript 1",
				transcriptionStatus = TranscriptionStatus.DONE,
			)
			val client = EntryFakeDiaryClient(entry)
			val player = mock<Player>(mode = MockMode.autoUnit)
			every { player.isAvailable } returns true

			setContent {
				CompositionLocalProvider(
					LocalLifecycleOwner provides EntryFakeLifecycleOwner(),
					LocalViewModelStoreOwner provides EntryFakeViewModelStoreOwner(),
				) {
					MaterialTheme {
						EntryDetailScreen(
							diaryClient = client,
							entryId = entry.id,
							onBack = {},
							player = player,
							transcriber = null,
						)
					}
				}
			}

			waitUntilAtLeastOneExists(hasText("Edit"))

			onNodeWithText("Edit").performClick()
			waitUntilAtLeastOneExists(hasText("Save") and isNotEnabled())

			onNodeWithText("Transcript 1").performTextClearance()
			waitUntilAtLeastOneExists(hasText("Save") and isNotEnabled())
			onNode(hasSetTextAction()).performTextInput("Edited")
			onNodeWithText("Save").assertIsEnabled()
			onNodeWithText("Save").performClick()
			waitUntilAtLeastOneExists(hasText("Edited"))
			assert(client.lastUpdateRequest?.transcriptionText == "Edited")
		}

	@Test
	fun transcribe_button_shows_transcribe_for_none_status() =
		runComposeUiTest {
			System.setProperty("voiceDiary.whisperAvailable", "true")
			val entry = VoiceDiaryEntry(
				id = Uuid.random(),
				title = "Recording 1",
				recordedAt = Clock.System.now(),
				duration = Duration.ZERO,
				transcriptionStatus = TranscriptionStatus.NONE,
			)
			val audio = byteArrayOf(1)
			val client = EntryFakeDiaryClient(entry, audio)
			val player = mock<Player>(mode = MockMode.autoUnit)
			val transcriber = ReadyTranscriber()
			every { player.isAvailable } returns true

			setContent {
				CompositionLocalProvider(
					LocalLifecycleOwner provides EntryFakeLifecycleOwner(),
					LocalViewModelStoreOwner provides EntryFakeViewModelStoreOwner(),
				) {
					MaterialTheme {
						EntryDetailScreen(
							diaryClient = client,
							entryId = entry.id,
							onBack = {},
							player = player,
							transcriber = transcriber,
						)
					}
				}
			}

			waitUntilAtLeastOneExists(hasText("Transcribe"))
		}

	@Test
	fun delete_calls_client_and_navigates_back() =
		runComposeUiTest {
			val entry = VoiceDiaryEntry(
				id = Uuid.random(),
				title = "Recording 1",
				recordedAt = Clock.System.now(),
				duration = Duration.ZERO,
				transcriptionText = "Transcript 1",
				transcriptionStatus = TranscriptionStatus.DONE,
			)
			val client = EntryFakeDiaryClient(entry)
			val player = mock<Player>(mode = MockMode.autoUnit)
			every { player.isAvailable } returns true
			var backCalled = false

			setContent {
				CompositionLocalProvider(
					LocalLifecycleOwner provides EntryFakeLifecycleOwner(),
					LocalViewModelStoreOwner provides EntryFakeViewModelStoreOwner(),
				) {
					MaterialTheme {
						EntryDetailScreen(
							diaryClient = client,
							entryId = entry.id,
							onBack = { backCalled = true },
							player = player,
							transcriber = null,
						)
					}
				}
			}

			waitUntilAtLeastOneExists(hasText("Delete"))

			onNodeWithText("Delete").performClick()
			waitUntilDoesNotExist(hasText("Delete"))
			assert(backCalled)
			assert(client.entries.value.isEmpty())
		}

	@Test
	fun delete_failure_shows_error_and_does_not_navigate_back() =
		runComposeUiTest {
			val entry = VoiceDiaryEntry(
				id = Uuid.random(),
				title = "Recording 1",
				recordedAt = Clock.System.now(),
				duration = Duration.ZERO,
				transcriptionText = "Transcript 1",
				transcriptionStatus = TranscriptionStatus.DONE,
			)
			val client = EntryFakeDiaryClient(entry, failDeletion = true)
			val player = mock<Player>(mode = MockMode.autoUnit)
			every { player.isAvailable } returns true
			var backCalled = false

			setContent {
				CompositionLocalProvider(
					LocalLifecycleOwner provides EntryFakeLifecycleOwner(),
					LocalViewModelStoreOwner provides EntryFakeViewModelStoreOwner(),
				) {
					MaterialTheme {
						EntryDetailScreen(
							diaryClient = client,
							entryId = entry.id,
							onBack = { backCalled = true },
							player = player,
							transcriber = null,
						)
					}
				}
			}

			waitUntilAtLeastOneExists(hasText("Delete"))

			onNodeWithText("Delete").performClick()
			waitUntilAtLeastOneExists(hasText("Error: fail delete"))
			assert(!backCalled)
			assert(client.entries.value.isNotEmpty())
		}

	@Test
	fun displays_placeholder_when_no_transcription() =
		runComposeUiTest {
			val entry = VoiceDiaryEntry(
				id = Uuid.random(),
				title = "Recording 1",
				recordedAt = Clock.System.now(),
				duration = Duration.ZERO,
				transcriptionText = null,
				transcriptionStatus = TranscriptionStatus.NONE,
			)
			val client = EntryFakeDiaryClient(entry)
			val player = mock<Player>(mode = MockMode.autoUnit)
			every { player.isAvailable } returns true

			setContent {
				CompositionLocalProvider(
					LocalLifecycleOwner provides EntryFakeLifecycleOwner(),
					LocalViewModelStoreOwner provides EntryFakeViewModelStoreOwner(),
				) {
					MaterialTheme {
						EntryDetailScreen(
							diaryClient = client,
							entryId = entry.id,
							onBack = {},
							player = player,
							transcriber = null,
						)
					}
				}
			}

			waitUntilAtLeastOneExists(hasText("Not yet transcribed"))
		}
}

@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
private class EntryFakeDiaryClient(
	entry: VoiceDiaryEntry,
	private val audio: ByteArray = byteArrayOf(0),
	private val failDeletion: Boolean = false,
) : DiaryClient(baseUrl = "", httpClient = HttpClient()) {
	private val _entries = MutableStateFlow(listOf(entry))
	override val entries: MutableStateFlow<List<VoiceDiaryEntry>> get() = _entries
	var lastUpdateRequest: UpdateTranscriptionRequest? = null

	override suspend fun createEntry(entry: VoiceDiaryEntry, audio: ByteArray): VoiceDiaryEntry = entry

	override suspend fun deleteEntry(id: Uuid) {
		if (failDeletion) {
			throw IllegalStateException("fail delete")
		}
		_entries.value = _entries.value.filterNot { it.id == id }
	}

	override suspend fun getAudio(id: Uuid): ByteArray = audio

	override suspend fun updateTranscription(id: Uuid, request: UpdateTranscriptionRequest) {
		lastUpdateRequest = request
		_entries.value = _entries.value.map {
			if (it.id == id) {
				it.copy(
					transcriptionText = request.transcriptionText,
					transcriptionStatus = request.transcriptionStatus,
				)
			} else {
				it
			}
		}
	}
}

private class EntryFakeLifecycleOwner : LifecycleOwner {
	private val registry = LifecycleRegistry(this).apply {
		currentState = Lifecycle.State.RESUMED
	}
	override val lifecycle: Lifecycle get() = registry
}

private class EntryFakeViewModelStoreOwner : ViewModelStoreOwner {
	override val viewModelStore: ViewModelStore = ViewModelStore()
}

private class ReadyTranscriber : Transcriber {
	override val modelManager = object : ModelDownloader {
		override val modelDownloadProgress = MutableStateFlow<Float?>(1f)
	}

	override suspend fun initialize() {}

	override suspend fun transcribe(buffer: Buffer): String = ""
}
