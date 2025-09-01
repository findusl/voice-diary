package de.lehrbaum.voiry.audio

import androidx.compose.runtime.Stable

@Stable
interface Player {
	val isAvailable: Boolean

	fun play(audio: ByteArray)

	fun stop()

	fun close()
}
