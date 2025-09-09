package de.lehrbaum.voiry.audio

import de.lehrbaum.voiry.voiceDiaryCacheDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
actual object AudioCache {
	private val cacheDir: Path = Path.of(voiceDiaryCacheDir(), "audio").also { it.createDirectories() }

	actual fun getAudio(id: Uuid): ByteArray? {
		val file = cacheDir.resolve("$id.wav")
		return if (file.exists()) file.readBytes() else null
	}

	actual fun putAudio(id: Uuid, bytes: ByteArray) {
		val file = cacheDir.resolve("$id.wav")
		file.writeBytes(bytes)
	}

	actual fun cacheRecording(bytes: ByteArray) {
		val file = cacheDir.resolve("recording-${System.currentTimeMillis()}.wav")
		file.writeBytes(bytes)
	}
}
