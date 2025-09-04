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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.lehrbaum.voiry.api.v1.DiaryClient
import de.lehrbaum.voiry.audio.Player
import de.lehrbaum.voiry.audio.Transcriber
import de.lehrbaum.voiry.audio.platformPlayer
import de.lehrbaum.voiry.audio.platformTranscriber
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class, ExperimentalTime::class, ExperimentalLayoutApi::class)
@Composable
fun EntryDetailScreen(
	diaryClient: DiaryClient,
	entryId: Uuid,
	onBack: () -> Unit,
	player: Player = platformPlayer,
	transcriber: Transcriber? = platformTranscriber,
) {
	val viewModel = viewModel<EntryDetailViewModel>(key = entryId.toString()) {
		EntryDetailViewModel(diaryClient, entryId, player, transcriber)
	}
	val state by viewModel.uiState.collectAsStateWithLifecycle()
	val entry = state.entry ?: return

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
				state.audio?.let {
					TextButton(
						onClick = { viewModel.togglePlayback() },
					) {
						Text(if (state.isPlaying) "Stop" else "Play")
					}
				}
				TranscribeButtonWithProgress(
					transcriber = viewModel.transcriber,
					onTranscribe = { viewModel.transcribe() },
				)
				TextButton(
					onClick = { viewModel.delete(onBack) },
				) {
					Text("Delete")
				}
			}
			if (state.error != null) {
				Text("Error: ${state.error}")
			}
		}
	}
}
