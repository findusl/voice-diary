package de.lehrbaum.voiry.audio

import kotlinx.io.Buffer

class AudioRecorder constructor() : Recorder {
	override val isAvailable = false

	override fun startRecording() {
		// Not implemented on Android yet
	}

	override fun stopRecording(): Result<Buffer> {
		return Result.failure(UnsupportedOperationException("Not implemented on Android yet"))
	}

	override fun close() {
		// no-op
	}
}
