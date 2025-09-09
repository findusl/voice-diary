package de.lehrbaum.voiry.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.waitUntilAtLeastOneExists
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
import de.lehrbaum.voiry.audio.AudioCache
import de.lehrbaum.voiry.audio.Player
import de.lehrbaum.voiry.audio.Recorder
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import dev.mokkery.verify
import io.ktor.client.HttpClient
import java.nio.file.Files
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.junit.experimental.categories.Category

@OptIn(ExperimentalTestApi::class, ExperimentalTime::class, ExperimentalUuidApi::class)
@Category(UiTest::class)
class NavigationViewModelTest {
	@Test
	fun opening_different_entries_uses_new_view_model() =
		runComposeUiTest {
			val entry1 = VoiceDiaryEntry(
				id = Uuid.random(),
				title = "Recording 1",
				recordedAt = Clock.System.now(),
				duration = Duration.ZERO,
				transcriptionText = "Transcript 1",
				transcriptionStatus = TranscriptionStatus.DONE,
			)
			val entry2 = VoiceDiaryEntry(
				id = Uuid.random(),
				title = "Recording 2",
				recordedAt = Clock.System.now(),
				duration = Duration.ZERO,
				transcriptionText = "Transcript 2",
				transcriptionStatus = TranscriptionStatus.DONE,
			)
			val client = NavigationFakeDiaryClient(listOf(entry1, entry2))
			val recorder = mock<Recorder>()
			every { recorder.isAvailable } returns false
			val player1 = mock<Player>(mode = MockMode.autoUnit)
			val player2 = mock<Player>(mode = MockMode.autoUnit)
			every { player1.isAvailable } returns true
			every { player2.isAvailable } returns true

			setContent {
				CompositionLocalProvider(
					LocalLifecycleOwner provides NavigationFakeLifecycleOwner(),
					LocalViewModelStoreOwner provides NavigationFakeViewModelStoreOwner(),
				) {
					MaterialTheme {
						var selectedEntryId by remember { mutableStateOf<Uuid?>(null) }
						if (selectedEntryId == null) {
							MainScreen(
								diaryClient = client,
								recorder = recorder,
								onEntryClick = { selectedEntryId = it.id },
								transcriber = null,
								audioCache = AudioCache(Files.createTempDirectory("navigationVM").toString()),
							)
						} else {
							val player =
								if (selectedEntryId == entry1.id) player1 else player2
							EntryDetailScreen(
								diaryClient = client,
								entryId = selectedEntryId!!,
								onBack = { selectedEntryId = null },
								player = player,
								transcriber = null,
							)
						}
					}
				}
			}

			waitUntilAtLeastOneExists(hasText("Recording 1"))

			onNodeWithText("Recording 1").performClick()
			waitUntilAtLeastOneExists(hasText("Transcript 1"))

			onNodeWithText("Back").performClick()
			waitUntilAtLeastOneExists(hasText("Recording 1"))
			verify { player1.close() }

			onNodeWithText("Recording 2").performClick()
			waitUntilAtLeastOneExists(hasText("Transcript 2"))

			onNodeWithText("Back").performClick()
			waitUntilAtLeastOneExists(hasText("Recording 2"))
			verify { player2.close() }
		}
}

@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
private class NavigationFakeDiaryClient(
	entries: List<VoiceDiaryEntry>,
) : DiaryClient(baseUrl = "", httpClient = HttpClient()) {
	private val _entries = MutableStateFlow<PersistentList<VoiceDiaryEntry>>(entries.toPersistentList())
	override val entries: MutableStateFlow<PersistentList<VoiceDiaryEntry>> get() = _entries

	override suspend fun getAudio(id: Uuid): ByteArray = byteArrayOf(0)
}

private class NavigationFakeLifecycleOwner : LifecycleOwner {
	private val registry = LifecycleRegistry(this).apply {
		currentState = Lifecycle.State.RESUMED
	}
	override val lifecycle: Lifecycle get() = registry
}

private class NavigationFakeViewModelStoreOwner : ViewModelStoreOwner {
	override val viewModelStore: ViewModelStore = ViewModelStore()
}
