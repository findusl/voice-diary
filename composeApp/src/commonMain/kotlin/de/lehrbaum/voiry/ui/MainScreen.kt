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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.findusl.wavrecorder.Recorder
import de.findusl.wavrecorder.platformRecorder
import de.lehrbaum.voiry.api.v1.DiaryClient
import de.lehrbaum.voiry.audio.Transcriber
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class, ExperimentalUuidApi::class)
@Composable
fun MainScreen(
	diaryClient: DiaryClient,
	recorder: Recorder = platformRecorder,
	onRequestAudioPermission: (() -> Unit)? = null,
	transcriber: Transcriber?,
	onEntryClick: (UiVoiceDiaryEntry) -> Unit,
	cacheAvailable: Boolean = true,
) {
	val viewModel = viewModel { MainViewModel(diaryClient, recorder, transcriber, cacheAvailable) }
	MainScreen(
		viewModel = viewModel,
		onRequestAudioPermission = onRequestAudioPermission,
		transcriber = transcriber,
		onEntryClick = onEntryClick,
	)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class, ExperimentalUuidApi::class)
@Composable
internal fun MainScreen(
	viewModel: MainViewModel,
	onRequestAudioPermission: (() -> Unit)? = null,
	transcriber: Transcriber?,
	onEntryClick: (UiVoiceDiaryEntry) -> Unit,
) {
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
			if (!state.recorderAvailable && !state.recorderUnavailableDismissed) {
				InfoBanner(
					text = "Audio recorder not available on this platform/device.",
					actionLabel = "Dismiss",
					onAction = { viewModel.dismissRecorderUnavailable() },
				)
			}
			if (state.cacheUnavailable && !state.cacheUnavailableDismissed) {
				InfoBanner(
					text = "Audio cache unavailable; recordings won't be stored locally.",
					actionLabel = "Dismiss",
					onAction = { viewModel.dismissCacheUnavailable() },
				)
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

	var showDatePicker by remember { mutableStateOf(false) }
	var showTimePicker by remember { mutableStateOf(false) }
	LaunchedEffect(state.pendingRecording) {
		if (state.pendingRecording == null) {
			showDatePicker = false
			showTimePicker = false
		}
	}
	val timeZone = remember { TimeZone.currentSystemDefault() }

	if (state.pendingRecording != null) {
		val localDateTime = state.pendingRecordedAt.toLocalDateTime(timeZone)
		val dateText = localDateTime.date.format(DATE_FORMAT)
		val timeText = localDateTime.time.format(TIME_FORMAT)

		AlertDialog(
			onDismissRequest = { viewModel.cancelSaveRecording() },
			title = { Text("Save Recording") },
			text = {
				Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
					TextField(
						value = state.pendingTitle,
						onValueChange = viewModel::updatePendingTitle,
						label = { Text("Title") },
					)
					Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
						Text("Recorded on")
						Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
							OutlinedButton(
								modifier = Modifier.testTag("saveRecordingDateButton"),
								onClick = { showDatePicker = true },
							) {
								Text(dateText)
							}
							OutlinedButton(
								modifier = Modifier.testTag("saveRecordingTimeButton"),
								onClick = { showTimePicker = true },
							) {
								Text(timeText)
							}
						}
					}
				}
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

		if (showDatePicker) {
			val datePickerState = rememberDatePickerState(
				initialSelectedDateMillis = state.pendingRecordedAt.toEpochMilliseconds(),
			)
			DatePickerDialog(
				onDismissRequest = { showDatePicker = false },
				confirmButton = {
					TextButton(
						onClick = {
							val selectedDateMillis = datePickerState.selectedDateMillis
							if (selectedDateMillis != null) {
								viewModel.updatePendingRecordedDate(selectedDateMillis)
								showDatePicker = false
							}
						},
						enabled = datePickerState.selectedDateMillis != null,
					) { Text("OK") }
				},
				dismissButton = {
					TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
				},
			) {
				DatePicker(state = datePickerState)
			}
		}

		if (showTimePicker) {
			val timePickerState = rememberTimePickerState(
				initialHour = localDateTime.hour,
				initialMinute = localDateTime.minute,
				is24Hour = true,
			)
			LaunchedEffect(state.pendingRecordedAt, showTimePicker) {
				if (showTimePicker) {
					val updated = state.pendingRecordedAt.toLocalDateTime(timeZone)
					timePickerState.hour = updated.hour
					timePickerState.minute = updated.minute
				}
			}
			AlertDialog(
				onDismissRequest = { showTimePicker = false },
				title = { Text("Select time") },
				text = { TimePicker(state = timePickerState) },
				confirmButton = {
					TextButton(
						onClick = {
							viewModel.updatePendingRecordedTime(
								timePickerState.hour,
								timePickerState.minute,
							)
							showTimePicker = false
						},
					) { Text("OK") }
				},
				dismissButton = {
					TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
				},
			)
		}
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
	var showDeleteDialog by remember { mutableStateOf(false) }
	ListItem(
		modifier = Modifier.fillMaxWidth().clickable { onClick() },
		headlineContent = { Text(entry.title) },
		supportingContent = {
			Text(entry.transcriptionText ?: entry.transcriptionStatus.displayName())
		},
		trailingContent = {
			Row(
				horizontalArrangement = Arrangement.spacedBy(8.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				TranscribeButtonWithProgress(
					transcriber = transcriber,
					onTranscribe = onTranscribeClick,
				)
				TextButton(onClick = { showDeleteDialog = true }) { Text("Delete") }
			}
		},
	)
	HorizontalDivider()
	if (showDeleteDialog) {
		ConfirmationDialog(
			title = "Delete entry?",
			text = "Are you sure you want to delete this entry?",
			confirmText = "Delete",
			dismissText = "Cancel",
			onConfirm = {
				showDeleteDialog = false
				onDeleteClick()
			},
			onDismiss = { showDeleteDialog = false },
		)
	}
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

private val DATE_FORMAT = LocalDate.Format { date(LocalDate.Formats.ISO) }
private val TIME_FORMAT = LocalTime.Format {
	hour()
	char(':')
	minute()
}
