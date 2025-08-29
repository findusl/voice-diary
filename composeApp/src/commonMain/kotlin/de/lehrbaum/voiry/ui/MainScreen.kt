package de.lehrbaum.voiry.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.lehrbaum.voiry.api.v1.DiaryClient
import de.lehrbaum.voiry.api.v1.TranscriptionStatus
import de.lehrbaum.voiry.api.v1.UpdateTranscriptionRequest
import de.lehrbaum.voiry.api.v1.VoiceDiaryEntry
import de.lehrbaum.voiry.audio.Recorder
import de.lehrbaum.voiry.audio.Transcriber
import de.lehrbaum.voiry.audio.isWhisperAvailable
import de.lehrbaum.voiry.audio.platformRecorder
import de.lehrbaum.voiry.audio.platformTranscriber
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.launch
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.write

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class, ExperimentalUuidApi::class)
@Composable
fun MainScreen(
	diaryClient: DiaryClient,
	recorder: Recorder = platformRecorder,
	onRequestAudioPermission: (() -> Unit)? = null,
	transcriber: Transcriber? = platformTranscriber,
	onEntryClick: (VoiceDiaryEntry) -> Unit,
) {
	val scope = rememberCoroutineScope()
	val entries by diaryClient.entries.collectAsStateWithLifecycle()
	var error by remember { mutableStateOf<String?>(null) }
	var isRecording by remember { mutableStateOf(false) }
	var pendingRecording by remember { mutableStateOf<Buffer?>(null) }
	var pendingTitle by remember { mutableStateOf("") }
	val canTranscribe by produceState(initialValue = false, transcriber) {
		value = transcriber != null && isWhisperAvailable()
	}

	DisposableEffect(recorder) {
		onDispose {
			try {
				recorder.close()
			} catch (_: Throwable) {
			}
		}
	}

	Scaffold(
		topBar = {
			TopAppBar(title = { Text("Voice Diary") })
		},
		floatingActionButton = {
			if (recorder.isAvailable) {
				ExtendedFloatingActionButton(
					onClick = {
						if (!isRecording) {
							val result = runCatching { recorder.startRecording() }
							result
								.onSuccess {
									isRecording = true
									error = null
								}.onFailure { e ->
									isRecording = false
									error = e.message ?: "Failed to start recording"
								}
						} else {
							scope.launch {
								val stopResult = recorder.stopRecording()
								stopResult
									.onSuccess { buffer ->
										pendingRecording = buffer
										isRecording = false
										error = null
									}.onFailure { e ->
										error = e.message
										isRecording = false
									}
							}
						}
					},
				) {
					Text(if (isRecording) "Stop" else "Record")
				}
			}
		},
	) { padding ->
		Column(
			modifier = Modifier
				.padding(padding)
				.fillMaxSize(),
		) {
			if (!recorder.isAvailable) {
				InfoBanner("Audio recorder not available on this platform/device.")
			}
			if (error != null) {
				val permissionRelated = error?.contains("permission", ignoreCase = true) == true && onRequestAudioPermission != null
				InfoBanner(
					text = "Error: $error",
					actionLabel = if (permissionRelated) "Grant permission" else null,
					onAction = if (permissionRelated) onRequestAudioPermission else null,
				)
			}
			when {
				entries.isEmpty() -> {
					Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
						Text("No recordings yet. Tap Record to add one.")
					}
				}

				else -> {
					LazyColumn(
						modifier = Modifier.fillMaxSize(),
						contentPadding = PaddingValues(vertical = 8.dp),
					) {
						items(entries, key = { it.id }) { entry ->
							EntryRow(
								entry = entry,
								onDelete = { toDelete ->
									scope.launch {
										runCatching {
											diaryClient.deleteEntry(toDelete.id)
										}.onFailure { e ->
											error = e.message
										}
									}
								},
								onTranscribe =
									if (canTranscribe) {
										{ toTranscribe ->
											scope.launch {
												runCatching {
													val bytes = diaryClient.getAudio(toTranscribe.id)
													val buffer = Buffer().apply { write(bytes) }
													val text = transcriber!!.transcribe(buffer)
													diaryClient.updateTranscription(
														toTranscribe.id,
														UpdateTranscriptionRequest(
															text,
															TranscriptionStatus.DONE,
															Clock.System.now(),
														),
													)
												}.onFailure { e ->
													error = e.message
													runCatching {
														diaryClient.updateTranscription(
															toTranscribe.id,
															UpdateTranscriptionRequest(
																null,
																TranscriptionStatus.FAILED,
																Clock.System.now(),
															),
														)
													}
												}
											}
										}
									} else {
										null
									},
								onClick = { onEntryClick(entry) },
							)
						}
					}
				}
			}
		}
	}

	if (pendingRecording != null) {
		AlertDialog(
			onDismissRequest = { pendingRecording = null },
			title = { Text("Save Recording") },
			text = {
				TextField(
					value = pendingTitle,
					onValueChange = { pendingTitle = it },
					label = { Text("Title") },
				)
			},
			confirmButton = {
				TextButton(
					onClick = {
						val buffer = pendingRecording
						if (buffer != null) {
							scope.launch {
								val bytes = buffer.readByteArray()
								val entry = VoiceDiaryEntry(
									id = Uuid.random(),
									title = pendingTitle,
									recordedAt = Clock.System.now(),
									duration = Duration.ZERO,
								)
								runCatching { diaryClient.createEntry(entry, bytes) }
									.onFailure { e -> error = e.message }
								pendingRecording = null
								pendingTitle = ""
							}
						}
					},
					enabled = pendingTitle.isNotBlank(),
				) { Text("Save") }
			},
			dismissButton = {
				TextButton(onClick = { pendingRecording = null }) { Text("Cancel") }
			},
		)
	}
}

@Composable
private fun EntryRow(
	entry: VoiceDiaryEntry,
	onDelete: (VoiceDiaryEntry) -> Unit,
	onTranscribe: ((VoiceDiaryEntry) -> Unit)? = null,
	onClick: () -> Unit,
) {
	ListItem(
		modifier = Modifier.fillMaxWidth().clickable { onClick() },
		headlineContent = { Text(entry.title) },
		supportingContent = {
			Text(entry.transcriptionText ?: entry.transcriptionStatus.name)
		},
		trailingContent = {
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				if (onTranscribe != null) {
					TextButton(onClick = { onTranscribe(entry) }) { Text("Transcribe") }
				}
				TextButton(onClick = { onDelete(entry) }) { Text("Delete") }
			}
		},
	)
	HorizontalDivider()
}

@Composable
private fun InfoBanner(
	text: String,
	actionLabel: String? = null,
	onAction: (() -> Unit)? = null,
) {
	Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(12.dp),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically,
		) {
			Text(text, modifier = Modifier.weight(1f))
			if (actionLabel != null && onAction != null) {
				TextButton(onClick = onAction) { Text(actionLabel) }
			}
		}
	}
}
