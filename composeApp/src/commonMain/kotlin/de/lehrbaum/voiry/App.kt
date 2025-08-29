package de.lehrbaum.voiry

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import de.lehrbaum.voiry.api.v1.DiaryClient
import de.lehrbaum.voiry.ui.EntryDetailScreen
import de.lehrbaum.voiry.ui.MainScreen
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.jetbrains.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
@Composable
@Preview
fun App(baseUrl: String = "http://localhost:8080", onRequestAudioPermission: (() -> Unit)? = null) {
	val diaryClient = remember { DiaryClient(baseUrl) }
	var selectedEntryId by remember { mutableStateOf<Uuid?>(null) }
	DisposableEffect(diaryClient) {
		onDispose { diaryClient.close() }
	}
	MaterialTheme {
		if (selectedEntryId == null) {
			MainScreen(
				diaryClient = diaryClient,
				onRequestAudioPermission = onRequestAudioPermission,
				onEntryClick = { entry -> selectedEntryId = entry.id },
			)
		} else {
			EntryDetailScreen(
				diaryClient = diaryClient,
				entryId = selectedEntryId!!,
				onBack = { selectedEntryId = null },
			)
		}
	}
}
