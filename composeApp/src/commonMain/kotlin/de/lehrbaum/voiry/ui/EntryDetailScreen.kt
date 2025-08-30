package de.lehrbaum.voiry.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.lehrbaum.voiry.api.v1.DiaryClient
import de.lehrbaum.voiry.api.v1.TranscriptionStatus
import de.lehrbaum.voiry.api.v1.UpdateTranscriptionRequest
import de.lehrbaum.voiry.audio.Player
import de.lehrbaum.voiry.audio.Transcriber
import de.lehrbaum.voiry.audio.isWhisperAvailable
import de.lehrbaum.voiry.audio.platformPlayer
import de.lehrbaum.voiry.audio.platformTranscriber
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.launch
import kotlinx.io.Buffer
import kotlinx.io.write

@OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class, ExperimentalTime::class)
@Composable
fun EntryDetailScreen(
	diaryClient: DiaryClient,
	entryId: Uuid,
	onBack: () -> Unit,
	player: Player = platformPlayer,
	transcriber: Transcriber? = platformTranscriber,
) {
	val scope = rememberCoroutineScope()
	val entryState by diaryClient.entryFlow(entryId).collectAsStateWithLifecycle(initialValue = null)
	val entry = entryState ?: return
	var audio by remember { mutableStateOf<ByteArray?>(null) }
	var isPlaying by remember { mutableStateOf(false) }
	var error by remember { mutableStateOf<String?>(null) }
	val canTranscribe by produceState(initialValue = false, transcriber) {
		value = transcriber != null && isWhisperAvailable()
	}

	androidx.compose.runtime.LaunchedEffect(entryId) {
		runCatching { diaryClient.getAudio(entryId) }
			.onSuccess { audio = it }
			.onFailure { e -> error = e.message }
	}

	DisposableEffect(player) {
		onDispose {
			player.close()
		}
	}

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text(entry.title) },
				navigationIcon = {
					TextButton(onClick = onBack) { Text("Back") }
				},
			)
		},
	) { padding ->
		Column(
			modifier = Modifier
				.padding(padding)
				.padding(16.dp)
				.fillMaxSize(),
			verticalArrangement = Arrangement.spacedBy(12.dp),
		) {
			Text("Recorded at: ${entry.recordedAt}")
			Text(entry.transcriptionText ?: entry.transcriptionStatus.name)
			audio?.let { data ->
				TextButton(
					onClick = {
						if (isPlaying) {
							player.stop()
						} else {
							player.play(data)
						}
						isPlaying = !isPlaying
					},
				) {
					Text(if (isPlaying) "Stop" else "Play")
				}
			}
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				if (canTranscribe) {
					audio?.let { data ->
						TextButton(
							onClick = {
								scope.launch {
									runCatching {
										val buffer = Buffer().apply { write(data) }
										val text = transcriber!!.transcribe(buffer)
										diaryClient.updateTranscription(
											entry.id,
											UpdateTranscriptionRequest(
												text,
												TranscriptionStatus.DONE,
												Clock.System.now(),
											),
										)
									}.onFailure { e -> error = e.message }
								}
							},
						) {
							Text("Re-transcribe")
						}
					}
				}
				TextButton(
					onClick = {
						scope.launch {
							runCatching { diaryClient.deleteEntry(entry.id) }
							onBack()
						}
					},
				) {
					Text("Delete")
				}
			}
			if (error != null) {
				Text("Error: $error")
			}
		}
	}
}
