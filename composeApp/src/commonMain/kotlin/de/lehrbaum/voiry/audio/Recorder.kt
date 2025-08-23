package de.lehrbaum.voiry.audio

import kotlinx.io.Buffer

interface Recorder {
	val isAvailable: Boolean

	fun startRecording()

	fun stopRecording(): Result<Buffer>

	fun close()
}
