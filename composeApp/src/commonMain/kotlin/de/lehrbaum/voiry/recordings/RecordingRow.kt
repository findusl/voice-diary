package de.lehrbaum.voiry.recordings

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import de.lehrbaum.voiry.audio.Transcriber
import kotlinx.coroutines.launch

/** Displays a recording with optional transcription support. */
@Composable
fun RecordingRow(
	recording: Recording,
	onDelete: (Recording) -> Unit,
	transcriber: Transcriber? = null,
	onTranscript: (Recording, String) -> Unit = { _, _ -> },
) {
	var transcript by remember { mutableStateOf(recording.transcript) }
	val scope = rememberCoroutineScope()

	ListItem(
		headlineContent = { Text(recording.title) },
		supportingContent = { Text(transcript) },
		trailingContent = {
			Row {
				if (transcriber != null) {
					TextButton(onClick = {
						scope.launch {
							val result = runCatching { transcriber.transcribe(recording.bytes) }
							result.onSuccess {
								transcript = it
								onTranscript(recording, it)
							}
						}
					}) { Text("Transcribe") }
				}
				TextButton(onClick = { onDelete(recording) }) { Text("Delete") }
			}
		},
	)
	HorizontalDivider()
}
