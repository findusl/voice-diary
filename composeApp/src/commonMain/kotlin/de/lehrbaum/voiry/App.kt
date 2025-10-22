package de.lehrbaum.voiry

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import de.lehrbaum.voiry.api.v1.DiaryClientImpl
import de.lehrbaum.voiry.audio.AudioCache
import de.lehrbaum.voiry.audio.Transcriber
import de.lehrbaum.voiry.audio.platformTranscriber
import de.lehrbaum.voiry.ui.EntryDetailScreen
import de.lehrbaum.voiry.ui.MainScreen
import de.lehrbaum.voiry.ui.UiVoiceDiaryEntry
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.jetbrains.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
@Composable
@Preview
fun App(baseUrl: String = BuildKonfig.BACKEND_URL, onRequestAudioPermission: (() -> Unit)? = null) {
	initLogging()
	val audioCache = remember { AudioCache() }
	val diaryClient = remember { DiaryClientImpl(baseUrl, audioCache = audioCache) }
	val transcriber: Transcriber? = remember { platformTranscriber }
	LaunchedEffect(transcriber) { transcriber?.initialize() }
	var selectedEntryId by remember { mutableStateOf<Uuid?>(null) }
	DisposableEffect(diaryClient) {
		onDispose { diaryClient.close() }
	}
	val onEntryClick: (UiVoiceDiaryEntry) -> Unit = remember {
		{ entry -> selectedEntryId = entry.id }
	}
	val onBack: () -> Unit = remember {
		{ selectedEntryId = null }
	}
	MaterialTheme {
		val entryId = selectedEntryId
		if (entryId == null) {
			MainScreen(
				diaryClient = diaryClient,
				onRequestAudioPermission = onRequestAudioPermission,
				transcriber = transcriber,
				onEntryClick = onEntryClick,
				cacheAvailable = audioCache.enabled,
			)
		} else {
			EntryDetailScreen(
				diaryClient = diaryClient,
				entryId = entryId,
				onBack = onBack,
				transcriber = transcriber,
			)
		}
	}
}
