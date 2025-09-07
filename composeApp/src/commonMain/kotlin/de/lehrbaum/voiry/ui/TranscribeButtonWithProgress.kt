package de.lehrbaum.voiry.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.lehrbaum.voiry.audio.Transcriber
import de.lehrbaum.voiry.audio.isWhisperAvailable

@Composable
fun TranscribeButtonWithProgress(
	transcriber: Transcriber?,
	onTranscribe: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val downloader = remember(transcriber) { transcriber?.modelManager }
	var whisperAvailable by remember(transcriber) { mutableStateOf<Boolean?>(null) }
	LaunchedEffect(transcriber) {
		whisperAvailable = if (transcriber != null) isWhisperAvailable() else false
	}
	if (transcriber == null || whisperAvailable != true || downloader == null) return

	val progress = downloader.modelDownloadProgress.collectAsState().value ?: return
	val onClick by rememberUpdatedState(onTranscribe)

	if (progress < 1f) {
		Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			val percent = (progress * 100).toInt()
			Text("$percent%")
		}
	} else {
		TextButton(onClick = onClick, modifier = modifier) {
			Text("Transcribe")
		}
	}
}
