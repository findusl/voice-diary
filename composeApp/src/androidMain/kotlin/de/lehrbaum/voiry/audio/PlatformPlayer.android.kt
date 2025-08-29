package de.lehrbaum.voiry.audio

actual val platformPlayer: Player = object : Player {
	override val isAvailable: Boolean = true

	override fun play(audio: ByteArray) {
	}

	override fun stop() {
	}

	override fun close() {
	}
}
