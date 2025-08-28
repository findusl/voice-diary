package de.lehrbaum.voiry

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import de.lehrbaum.voiry.api.v1.DiaryClient
import de.lehrbaum.voiry.ui.MainScreen
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import org.jetbrains.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
@Composable
@Preview
fun App(baseUrl: String = "http://localhost:8080", onRequestAudioPermission: (() -> Unit)? = null) {
	val diaryClient = remember { DiaryClient(baseUrl) }
	DisposableEffect(diaryClient) {
		onDispose { diaryClient.close() }
	}
	MaterialTheme {
		MainScreen(diaryClient = diaryClient, onRequestAudioPermission = onRequestAudioPermission)
	}
}
