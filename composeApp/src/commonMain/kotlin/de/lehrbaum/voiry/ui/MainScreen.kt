package de.lehrbaum.voiry.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.lehrbaum.voiry.audio.AudioRecorder
import de.lehrbaum.voiry.recordings.MockRecordingRepository
import de.lehrbaum.voiry.recordings.Recording
import de.lehrbaum.voiry.recordings.RecordingRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    repository: RecordingRepository = remember { MockRecordingRepository() },
    enableRecording: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    var recordings by remember { mutableStateOf<List<Recording>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    val recorder: AudioRecorder? = remember(enableRecording) { if (enableRecording) AudioRecorder() else null }
    var isRecording by remember { mutableStateOf(false) }

    DisposableEffect(recorder) {
        onDispose {
            try { recorder?.close() } catch (_: Throwable) {}
        }
    }

    LaunchedEffect(Unit) {
        runCatching { repository.listRecordings() }
            .onSuccess { recordings = it }
            .onFailure { error = it.message }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Voice Diary") })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (recorder?.isAvailable != true) return@ExtendedFloatingActionButton
                    if (!isRecording) {
                        runCatching { recorder.startRecording() }
                        isRecording = true
                    } else {
                        scope.launch {
                            val stopResult = recorder.stopRecording()
                            stopResult.onSuccess { buffer ->
                                // Save to server (mock)
                                runCatching { repository.saveRecording(buffer) }
                                    .onSuccess { newRec ->
                                        recordings = listOf(newRec) + recordings
                                        isRecording = false
                                    }
                                    .onFailure { e ->
                                        error = e.message
                                        isRecording = false
                                    }
                            }.onFailure { e ->
                                error = e.message
                                isRecording = false
                            }
                        }
                    }
                }
            ) {
                Text(if (isRecording) "Stop" else "Record")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (recorder?.isAvailable != true) {
                InfoBanner("Audio recorder not available on this platform/device.")
            }
            if (error != null) {
                InfoBanner("Error: $error")
            }
            when {
                loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                recordings.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No recordings yet. Tap Record to add one.")
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(recordings, key = { it.id }) { rec ->
                            RecordingRow(rec)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingRow(rec: Recording) {
    ListItem(
        headlineContent = { Text(rec.title) },
        supportingContent = { Text("${rec.bytes.size} bytes") }
    )
    HorizontalDivider()
}

@Composable
private fun InfoBanner(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(text, modifier = Modifier.padding(12.dp))
    }
}
