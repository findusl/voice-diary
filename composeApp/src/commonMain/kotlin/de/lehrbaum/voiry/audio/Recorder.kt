package de.lehrbaum.voiry.audio

import androidx.compose.runtime.Stable
import kotlinx.io.Buffer

@Stable
interface Recorder {
	val isAvailable: Boolean

	fun startRecording()

	fun stopRecording(): Result<Buffer>

	fun close()
}
