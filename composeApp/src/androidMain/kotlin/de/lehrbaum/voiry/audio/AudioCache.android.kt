package de.lehrbaum.voiry.audio

import de.lehrbaum.voiry.voiceDiaryCacheDir
import java.io.File
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
actual object AudioCache {
	private val cacheDir = File(voiceDiaryCacheDir(), "audio").apply { mkdirs() }

	actual fun getAudio(id: Uuid): ByteArray? {
		val file = File(cacheDir, "$id.wav")
		return if (file.exists()) file.readBytes() else null
	}

	actual fun putAudio(id: Uuid, bytes: ByteArray) {
		val file = File(cacheDir, "$id.wav")
		file.writeBytes(bytes)
	}

	actual fun cacheRecording(bytes: ByteArray) {
		val file = File(cacheDir, "recording-${System.currentTimeMillis()}.wav")
		file.writeBytes(bytes)
	}
}
