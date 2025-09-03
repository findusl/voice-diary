package de.lehrbaum.voiry.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.lehrbaum.voiry.api.v1.DiaryClient
import de.lehrbaum.voiry.api.v1.TranscriptionStatus
import de.lehrbaum.voiry.api.v1.UpdateTranscriptionRequest
import de.lehrbaum.voiry.audio.Player
import de.lehrbaum.voiry.audio.Transcriber
import de.lehrbaum.voiry.audio.platformPlayer
import de.lehrbaum.voiry.audio.platformTranscriber
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.io.Buffer

@OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class, ExperimentalTime::class, ExperimentalLayoutApi::class)
@Composable
fun EntryDetailScreen(
	diaryClient: DiaryClient,
	entryId: Uuid,
	onBack: () -> Unit,
	player: Player = platformPlayer,
	transcriber: Transcriber? = platformTranscriber,
) {
	val scope = rememberCoroutineScope()
	val entryFlow = remember(entryId) { diaryClient.entryFlow(entryId) }
	val entry = entryFlow.collectAsStateWithLifecycle().value ?: return
	var audio by remember { mutableStateOf<ByteArray?>(null) }
	var isPlaying by remember { mutableStateOf(false) }
	var error by remember { mutableStateOf<String?>(null) }
	var isEditing by remember { mutableStateOf(false) }
	var editedText by remember { mutableStateOf(entry.transcriptionText ?: "") }
	var isSaving by remember { mutableStateOf(false) }

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
			val recordedAtFormatted =
				kotlinx.datetime.Instant
					.fromEpochMilliseconds(
						entry.recordedAt.toEpochMilliseconds(),
					).toLocalDateTime(TimeZone.currentSystemDefault())
					.run {
						buildString {
							append(year)
							append('-')
							append(monthNumber.toString().padStart(2, '0'))
							append('-')
							append(dayOfMonth.toString().padStart(2, '0'))
							append(' ')
							append(hour.toString().padStart(2, '0'))
							append(':')
							append(minute.toString().padStart(2, '0'))
						}
					}
			Text("Recorded at: $recordedAtFormatted")
			if (isEditing) {
				OutlinedTextField(
					value = editedText,
					onValueChange = { editedText = it },
					modifier = Modifier.fillMaxWidth(),
				)
				Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					TextButton(
						enabled = !isSaving,
						onClick = {
							isEditing = false
							editedText = entry.transcriptionText ?: ""
						},
					) { Text("Cancel") }
					TextButton(
						enabled =
							!isSaving &&
								editedText.isNotBlank() &&
								editedText != (entry.transcriptionText ?: ""),
						onClick = {
							scope.launch {
								isSaving = true
								runCatching {
									diaryClient.updateTranscription(
										entry.id,
										UpdateTranscriptionRequest(
											editedText,
											TranscriptionStatus.DONE,
											Clock.System.now(),
										),
									)
								}.onSuccess {
									isEditing = false
								}.onFailure { e -> error = e.message }
								isSaving = false
							}
						},
					) { Text("Save") }
				}
				if (isSaving) {
					CircularProgressIndicator()
				}
			} else {
				Text(entry.transcriptionText ?: entry.transcriptionStatus.displayName())
				TextButton(
					onClick = {
						editedText = entry.transcriptionText ?: ""
						isEditing = true
					},
				) { Text("Edit") }
			}
			FlowRow(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(8.dp),
				verticalArrangement = Arrangement.spacedBy(8.dp),
			) {
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
				val latestAudio by rememberUpdatedState(audio)
				val setError by rememberUpdatedState<(String?) -> Unit> { error = it }
				val transcribeAction = remember(diaryClient, transcriber, entry.id, scope, setError) {
					{
						val audioData = latestAudio
						val t = transcriber
						if (audioData != null && t != null) {
							scope.launch {
								transcribeEntry(
									diaryClient,
									t,
									entry.id,
									audioData,
								).onFailure { e -> setError(e.message) }
							}
						} else if (t == null) {
							setError("Transcriber unavailable")
						}
						Unit
					}
				}
				TranscribeButtonWithProgress(transcriber = transcriber, onTranscribe = transcribeAction)
				TextButton(
					onClick = {
						scope.launch {
							runCatching { diaryClient.deleteEntry(entry.id) }
								.onSuccess { onBack() }
								.onFailure { e -> error = e.message }
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

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
private suspend fun transcribeEntry(
	diaryClient: DiaryClient,
	transcriber: Transcriber,
	entryId: Uuid,
	audio: ByteArray,
): Result<Unit> =
	runCatching {
		val buffer = Buffer().apply { write(audio) }
		val text = transcriber.transcribe(buffer)
		diaryClient.updateTranscription(
			entryId,
			UpdateTranscriptionRequest(
				text,
				TranscriptionStatus.DONE,
				Clock.System.now(),
			),
		)
	}
