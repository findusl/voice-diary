package de.lehrbaum.voiry.audio

import de.lehrbaum.voiry.voiceDiaryCacheDir
import kotlin.io.use
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.io.Buffer
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

@OptIn(ExperimentalUuidApi::class)
object AudioCache {
	private val fileSystem = SystemFileSystem
	private val cacheDir = Path(voiceDiaryCacheDir(), "audio").also { fileSystem.createDirectories(it) }

	fun getAudio(id: Uuid): ByteArray? {
		val path = Path(cacheDir, "$id.wav")
		if (!fileSystem.exists(path)) return null
		val buffer = Buffer()
		fileSystem.source(path).use { source ->
			while (true) {
				val read = source.readAtMostTo(buffer, 8192)
				if (read == -1L) break
			}
		}
		return buffer.readByteArray()
	}

	fun putAudio(id: Uuid, bytes: ByteArray) {
		val path = Path(cacheDir, "$id.wav")
		fileSystem.sink(path, append = false).use { sink ->
			val buffer = Buffer().apply { write(bytes) }
			sink.write(buffer, buffer.size)
			sink.flush()
		}
	}

	fun cacheRecording(bytes: ByteArray) {
		val path = Path(cacheDir, "recording-${'$'}{System.currentTimeMillis()}.wav")
		fileSystem.sink(path, append = false).use { sink ->
			val buffer = Buffer().apply { write(bytes) }
			sink.write(buffer, buffer.size)
			sink.flush()
		}
	}
}
