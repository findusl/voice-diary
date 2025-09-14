package de.lehrbaum.voiry

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import ca.gosyer.appdirs.impl.attachAppDirs

class MainActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		enableEdgeToEdge()
		super.onCreate(savedInstanceState)

		application.attachAppDirs()

		initLogging()

		// Request microphone permission at startup (API 23+)
		if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
			requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_RECORD_AUDIO)
		}

		setContent {
			App(
				onRequestAudioPermission = {
					if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
						requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_RECORD_AUDIO)
					}
				},
			)
		}
	}

	companion object {
		private const val REQ_RECORD_AUDIO = 1001
	}
}

@Preview
@Composable
fun AppAndroidPreview() {
	App()
}
