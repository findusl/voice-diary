package de.lehrbaum.voiry.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilAtLeastOneExists
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
import de.lehrbaum.voiry.audio.ModelDownloader
import de.lehrbaum.voiry.audio.Transcriber
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
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.io.Buffer
import org.junit.Test
import org.junit.experimental.categories.Category

@OptIn(ExperimentalTestApi::class, ExperimentalTime::class, ExperimentalUuidApi::class)
@Category(UiTest::class)
class TranscribeButtonWithProgressTest {
	@Test
	fun progress_persists_across_screens() =
		runComposeUiTest {
			System.setProperty("voiceDiary.whisperAvailable", "true")
			val entry = VoiceDiaryEntry(
				id = Uuid.random(),
				title = "Recording 1",
				recordedAt = Clock.System.now(),
				duration = Duration.ZERO,
				transcriptionText = "Transcript 1",
				transcriptionStatus = TranscriptionStatus.DONE,
			)
			val client = singleEntryDiaryClient(entry)
			val recorder = mock<Recorder>()
			every { recorder.isAvailable } returns false
			val transcriber = FakeProgressTranscriber()

			setContent {
				CompositionLocalProvider(
					LocalLifecycleOwner provides ProgressFakeLifecycleOwner(),
					LocalViewModelStoreOwner provides ProgressFakeViewModelStoreOwner(),
				) {
					MaterialTheme {
						var entryId by remember { mutableStateOf<Uuid?>(null) }
						if (entryId == null) {
							MainScreen(
								diaryClient = client,
								recorder = recorder,
								transcriber = transcriber,
								onEntryClick = { entry -> entryId = entry.id },
							)
						} else {
							EntryDetailScreen(
								diaryClient = client,
								entryId = entryId!!,
								onBack = { entryId = null },
								transcriber = transcriber,
							)
						}
					}
				}
			}

			waitUntilAtLeastOneExists(hasText("50%"))
			onNodeWithText("Recording 1").performClick()
			waitUntilAtLeastOneExists(hasText("50%"))
		}

	@Test
	fun shows_button_after_download() =
		runComposeUiTest {
			System.setProperty("voiceDiary.whisperAvailable", "true")
			val entry = VoiceDiaryEntry(
				id = Uuid.random(),
				title = "Recording 1",
				recordedAt = Clock.System.now(),
				duration = Duration.ZERO,
				transcriptionText = null,
				transcriptionStatus = TranscriptionStatus.NONE,
			)
			val client = singleEntryDiaryClient(entry)
			val recorder = mock<Recorder>()
			every { recorder.isAvailable } returns false
			val transcriber = FakeProgressTranscriber(0f)

			setContent {
				CompositionLocalProvider(
					LocalLifecycleOwner provides ProgressFakeLifecycleOwner(),
					LocalViewModelStoreOwner provides ProgressFakeViewModelStoreOwner(),
				) {
					MaterialTheme {
						MainScreen(
							diaryClient = client,
							recorder = recorder,
							transcriber = transcriber,
							onEntryClick = {},
						)
					}
				}
			}

			waitUntilAtLeastOneExists(hasText("Recording 1"))
			onAllNodesWithText("Transcribe").assertCountEquals(0)
			transcriber.finishDownload()
			waitUntilAtLeastOneExists(hasText("Transcribe"))
		}

	@Test
	fun starts_model_initialization_only_after_transcribe_is_clicked() =
		runComposeUiTest {
			System.setProperty("voiceDiary.whisperAvailable", "true")
			val entry = VoiceDiaryEntry(
				id = Uuid.random(),
				title = "Recording 1",
				recordedAt = Clock.System.now(),
				duration = Duration.ZERO,
				transcriptionText = null,
				transcriptionStatus = TranscriptionStatus.NONE,
			)
			val client = singleEntryDiaryClient(entry)
			val recorder = mock<Recorder>()
			every { recorder.isAvailable } returns false
			val transcriber = FakeProgressTranscriber(initial = null)

			setContent {
				CompositionLocalProvider(
					LocalLifecycleOwner provides ProgressFakeLifecycleOwner(),
					LocalViewModelStoreOwner provides ProgressFakeViewModelStoreOwner(),
				) {
					MaterialTheme {
						MainScreen(
							diaryClient = client,
							recorder = recorder,
							transcriber = transcriber,
							onEntryClick = {},
						)
					}
				}
			}

			waitUntilAtLeastOneExists(hasText("Transcribe"))
			assertEquals(0, transcriber.initializeCalls)
			onNodeWithText("Transcribe").performClick()
			waitUntil(timeoutMillis = 5_000) { transcriber.initializeCalls == 1 }
		}
}

private class FakeProgressTranscriber(initial: Float? = 0.5f) : Transcriber {
	private val downloadProgress = MutableStateFlow<Float?>(initial)

	override val modelManager = object : ModelDownloader {
		override val modelDownloadProgress = downloadProgress
	}

	var initializeCalls = 0
		private set

	override suspend fun initialize() {
		initializeCalls++
		downloadProgress.value = 0f
	}

	override suspend fun transcribe(buffer: Buffer, initialPrompt: String?): String = ""

	fun finishDownload() {
		downloadProgress.value = 1f
	}
}

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
private fun singleEntryDiaryClient(entry: VoiceDiaryEntry): DiaryClient {
	val entriesFlow = MutableStateFlow(listOf(entry).toPersistentList())
	return mock<DiaryClient> {
		every { entries } returns entriesFlow
		every { connectionError } returns MutableStateFlow(null)
		every { entryFlow(entry.id) } returns MutableStateFlow(entry)
		everySuspend { deleteEntry(entry.id) } calls { _ -> entriesFlow.value = persistentListOf() }
		everySuspend { getAudio(entry.id) } returns byteArrayOf(0)
		everySuspend { updateTranscription(entry.id, any()) } returns Unit
	}
}

private class ProgressFakeLifecycleOwner : LifecycleOwner {
	private val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
	override val lifecycle: Lifecycle get() = registry
}

private class ProgressFakeViewModelStoreOwner : ViewModelStoreOwner {
	private val store = ViewModelStore()
	override val viewModelStore: ViewModelStore get() = store
}
