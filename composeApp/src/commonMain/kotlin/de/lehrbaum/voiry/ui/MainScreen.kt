package de.lehrbaum.voiry.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.lehrbaum.voiry.audio.Recorder
import de.lehrbaum.voiry.audio.platformRecorder
import de.lehrbaum.voiry.recordings.MockRecordingRepository
import de.lehrbaum.voiry.recordings.Recording
import de.lehrbaum.voiry.recordings.RecordingRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    repository: RecordingRepository = remember { MockRecordingRepository() },
    recorder: Recorder = platformRecorder,
    onRequestAudioPermission: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    var recordings by remember { mutableStateOf<List<Recording>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var isRecording by remember { mutableStateOf(false) }

    DisposableEffect(recorder) {
        onDispose {
            try { recorder.close() } catch (_: Throwable) {}
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
            if (recorder.isAvailable) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (!isRecording) {
                            val result = runCatching { recorder.startRecording() }
                            result.onSuccess {
                                isRecording = true
                                error = null
                            }.onFailure { e ->
                                isRecording = false
                                error = e.message ?: "Failed to start recording"
                            }
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
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
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
private fun InfoBanner(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text, modifier = Modifier.weight(1f))
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}
