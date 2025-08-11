package de.lehrbaum.voiry.audio

import kotlinx.io.Buffer

actual class AudioRecorder actual constructor() {
	actual val isAvailable = false

	actual fun startRecording() {
		TODO("Not yet implemented")
	}

	actual fun stopRecording(): Result<Buffer> {
		TODO("Not yet implemented")
	}

	actual fun close() {
	}
}
