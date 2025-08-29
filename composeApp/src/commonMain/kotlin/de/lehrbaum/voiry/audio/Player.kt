package de.lehrbaum.voiry.audio

interface Player {
	val isAvailable: Boolean

	fun play(audio: ByteArray)

	fun stop()

	fun close()
}
