@file:OptIn(ExperimentalBasicSound::class)

package de.lehrbaum.voiry.audio

import app.lexilabs.basic.sound.Audio
import app.lexilabs.basic.sound.ExperimentalBasicSound
import java.io.File

actual val platformPlayer: Player = object : Player {
	override val isAvailable: Boolean = true

	private var audio: Audio? = null
	private var tempFile: File? = null

	override fun play(audio: ByteArray) {
		stop()
		val file = File.createTempFile("entry", ".wav")
		file.writeBytes(audio)
		tempFile = file
		this.audio = Audio(file.absolutePath).also {
			it.load()
			it.play()
		}
	}

	override fun stop() {
		audio?.stop()
		audio?.release()
		audio = null
		tempFile?.delete()
		tempFile = null
	}

	override fun close() {
		stop()
	}
}
