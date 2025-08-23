package de.lehrbaum.voiry

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import de.lehrbaum.voiry.ui.MainScreen
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App(onRequestAudioPermission: (() -> Unit)? = null) {
	MaterialTheme {
		MainScreen(onRequestAudioPermission = onRequestAudioPermission)
	}
}
