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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.lehrbaum.voiry.api.v1.DiaryClient
import de.lehrbaum.voiry.audio.Player
import de.lehrbaum.voiry.audio.Transcriber
import de.lehrbaum.voiry.audio.platformPlayer
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime

private val SIMPLE_FORMAT = LocalDateTime.Format {
	date(LocalDate.Formats.ISO)
	char(' ')
	hour()
	char(':')
	minute()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class, ExperimentalTime::class, ExperimentalLayoutApi::class)
@Composable
fun EntryDetailScreen(
	diaryClient: DiaryClient,
	entryId: Uuid,
	onBack: () -> Unit,
	player: Player = platformPlayer,
	transcriber: Transcriber?,
) {
	val owner = remember {
		object : ViewModelStoreOwner {
			override val viewModelStore = ViewModelStore()
		}
	}
	DisposableEffect(Unit) { onDispose { owner.viewModelStore.clear() } }
	val viewModel =
		viewModel<EntryDetailViewModel>(
			viewModelStoreOwner = owner,
			key = entryId.toString(),
		) { EntryDetailViewModel(diaryClient, entryId, player, transcriber) }
	val state by viewModel.uiState.collectAsStateWithLifecycle()
	val entry = state.entry ?: return

	var showDeleteDialog by remember { mutableStateOf(false) }

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text(entry.title) },
				navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
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
				entry.recordedAt.toLocalDateTime(TimeZone.currentSystemDefault()).format(SIMPLE_FORMAT)
			Text("Recorded at: $recordedAtFormatted")
			if (state.isEditing) {
				OutlinedTextField(
					value = state.editedText,
					onValueChange = { viewModel.updateEditedText(it) },
					modifier = Modifier.fillMaxWidth(),
				)
				Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					TextButton(
						enabled = !state.isSaving,
						onClick = { viewModel.cancelEdit() },
					) { Text("Cancel") }
					TextButton(
						enabled =
							!state.isSaving &&
								state.editedText.isNotBlank() &&
								state.editedText != (entry.transcriptionText ?: ""),
						onClick = { viewModel.saveEdit() },
					) { Text("Save") }
				}
				if (state.isSaving) {
					CircularProgressIndicator()
				}
			} else {
				Text(entry.transcriptionText ?: entry.transcriptionStatus.displayName())
				TextButton(
					onClick = { viewModel.startEditing() },
				) { Text("Edit") }
			}
			FlowRow(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(8.dp),
				verticalArrangement = Arrangement.spacedBy(8.dp),
			) {
				TextButton(
					onClick = { viewModel.togglePlayback() },
				) {
					Text(if (state.isPlaying) "Stop" else "Play")
				}
				TranscribeButtonWithProgress(
					transcriber = viewModel.transcriber,
					onTranscribe = { viewModel.transcribe() },
				)
				TextButton(onClick = { showDeleteDialog = true }) { Text("Delete") }
			}
			if (state.error != null) {
				Text("Error: ${state.error}")
			}
		}
	}
	if (showDeleteDialog) {
		ConfirmationDialog(
			title = "Delete entry?",
			text = "Are you sure you want to delete this entry?",
			confirmText = "Delete",
			dismissText = "Cancel",
			onConfirm = {
				showDeleteDialog = false
				viewModel.delete(onBack)
			},
			onDismiss = { showDeleteDialog = false },
		)
	}
}
