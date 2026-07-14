package de.lehrbaum.voiry

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ca.gosyer.appdirs.impl.attachAppDirs
import de.lehrbaum.voiry.audio.AudioPermissionRequester

class MainActivity : ComponentActivity() {
	private var pendingAudioPermissionResult: ((Boolean) -> Unit)? = null
	private var localNetworkPermissionGranted by mutableStateOf(false)

	private val audioPermissionLauncher =
		registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
			pendingAudioPermissionResult?.invoke(granted)
			pendingAudioPermissionResult = null
		}

	private val localNetworkPermissionLauncher =
		registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
			localNetworkPermissionGranted = granted
		}

	private val audioPermissionRequester = AudioPermissionRequester { onResult ->
		if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
			onResult(true)
		} else {
			pendingAudioPermissionResult = onResult
			audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		application.attachAppDirs()

		localNetworkPermissionGranted = hasLocalNetworkPermission()
		setContent {
			if (localNetworkPermissionGranted) {
				App(
					baseUrl = BuildConfig.BACKEND_URL,
					audioPermissionRequester = audioPermissionRequester,
				)
			} else {
				LocalNetworkPermissionRequired(
					onRequestPermission = { requestLocalNetworkPermission() },
				)
			}
		}

		if (!localNetworkPermissionGranted) {
			requestLocalNetworkPermission()
		}
	}

	override fun onDestroy() {
		pendingAudioPermissionResult?.invoke(false)
		pendingAudioPermissionResult = null
		super.onDestroy()
	}

	private fun hasLocalNetworkPermission(): Boolean =
		Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN ||
			checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

	private fun requestLocalNetworkPermission() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN) {
			localNetworkPermissionGranted = true
		} else {
			localNetworkPermissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
		}
	}
}

@Composable
private fun LocalNetworkPermissionRequired(onRequestPermission: () -> Unit) {
	MaterialTheme {
		Surface(modifier = Modifier.fillMaxSize()) {
			Column(
				modifier = Modifier.padding(24.dp),
				verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
				horizontalAlignment = Alignment.CenterHorizontally,
			) {
				Text("Voice Diary needs local network access to connect to your diary server.")
				Button(onClick = onRequestPermission) {
					Text("Allow local network access")
				}
			}
		}
	}
}

@Preview
@Composable
fun AppAndroidPreview() {
	App(baseUrl = BuildConfig.BACKEND_URL)
}
