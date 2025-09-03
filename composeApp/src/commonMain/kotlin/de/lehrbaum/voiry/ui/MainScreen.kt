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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.lehrbaum.voiry.api.v1.DiaryClient
import de.lehrbaum.voiry.audio.Recorder
import de.lehrbaum.voiry.audio.Transcriber
import de.lehrbaum.voiry.audio.platformRecorder
import de.lehrbaum.voiry.audio.platformTranscriber
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class, ExperimentalUuidApi::class)
@Composable
fun MainScreen(
	diaryClient: DiaryClient,
	recorder: Recorder = platformRecorder,
	onRequestAudioPermission: (() -> Unit)? = null,
	transcriber: Transcriber? = platformTranscriber,
	onEntryClick: (UiVoiceDiaryEntry) -> Unit,
) {
	val viewModel = viewModel { MainViewModel(diaryClient, recorder, transcriber) }
	val state by viewModel.uiState.collectAsStateWithLifecycle()
	val recordClick = remember(viewModel) {
		{
			val isRecording = viewModel.uiState.value.isRecording
			if (!isRecording) viewModel.startRecording() else viewModel.stopRecording()
		}
	}

	Scaffold(
		topBar = { TopAppBar(title = { Text("Voice Diary") }) },
		floatingActionButton = {
			if (state.recorderAvailable) {
				ExtendedFloatingActionButton(onClick = recordClick) {
					Text(if (state.isRecording) "Stop" else "Record")
				}
			}
		},
	) { padding ->
		Column(
			modifier = Modifier
				.padding(padding)
				.fillMaxSize(),
		) {
			if (!state.recorderAvailable) {
				InfoBanner("Audio recorder not available on this platform/device.")
			}
			if (state.error != null) {
				val permissionRelated =
					state.error?.contains("permission", ignoreCase = true) == true && onRequestAudioPermission != null
				InfoBanner(
					text = "Error: ${state.error}",
					actionLabel = if (permissionRelated) "Grant permission" else null,
					onAction = if (permissionRelated) onRequestAudioPermission else null,
				)
			}
			when {
				state.entries.isEmpty() -> {
					Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
						Text("No recordings yet. Tap Record to add one.")
					}
				}

				else -> {
					val onDelete = remember(viewModel) { viewModel::deleteEntry }
					LazyColumn(
						modifier = Modifier.fillMaxSize(),
						contentPadding = PaddingValues(vertical = 8.dp),
					) {
						items(state.entries, key = { it.id }) { entry ->
							val onClick = remember(entry.id, onEntryClick) {
								{ onEntryClick(entry) }
							}
							EntryRow(
								entry = entry,
								onDelete = onDelete,
								transcriber = transcriber,
								onTranscribe = { viewModel.transcribe(entry) },
								onClick = onClick,
							)
						}
					}
				}
			}
		}
	}

	if (state.pendingRecording != null) {
		AlertDialog(
			onDismissRequest = { viewModel.cancelSaveRecording() },
			title = { Text("Save Recording") },
			text = {
				TextField(
					value = state.pendingTitle,
					onValueChange = viewModel::updatePendingTitle,
					label = { Text("Title") },
				)
			},
			confirmButton = {
				TextButton(
					onClick = { viewModel.saveRecording() },
					enabled = state.pendingTitle.isNotBlank(),
				) { Text("Save") }
			},
			dismissButton = {
				TextButton(onClick = { viewModel.cancelSaveRecording() }) { Text("Cancel") }
			},
		)
	}
}

@OptIn(ExperimentalUuidApi::class)
@Composable
private fun EntryRow(
	entry: UiVoiceDiaryEntry,
	onDelete: (UiVoiceDiaryEntry) -> Unit,
	transcriber: Transcriber?,
	onTranscribe: (UiVoiceDiaryEntry) -> Unit,
	onClick: () -> Unit,
) {
	val onDeleteClick = remember(entry.id, onDelete) { { onDelete(entry) } }
	val onTranscribeClick = remember(entry.id, onTranscribe) { { onTranscribe(entry) } }
	ListItem(
		modifier = Modifier.fillMaxWidth().clickable { onClick() },
		headlineContent = { Text(entry.title) },
		supportingContent = {
			Text(entry.transcriptionText ?: entry.transcriptionStatus.displayName())
		},
		trailingContent = {
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
				TranscribeButtonWithProgress(
					transcriber = transcriber,
					onTranscribe = onTranscribeClick,
				)
				TextButton(onClick = onDeleteClick) { Text("Delete") }
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
