package de.lehrbaum.voiry.audio

import de.lehrbaum.voiry.voiceDiaryCacheDir
import io.github.aakira.napier.Napier
import kotlin.io.use
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.write

@OptIn(ExperimentalUuidApi::class)
class AudioCache(private val baseDir: String = voiceDiaryCacheDir()) {
	private val fileSystem = SystemFileSystem

	private val cacheDir: Path? = runCatching {
		Path(baseDir, "audio").also { fileSystem.createDirectories(it) }
	}.onFailure { Napier.i("Audio cache disabled: ${it.message}") }.getOrNull()

	val enabled: Boolean
		get() = cacheDir != null

	fun getAudio(id: Uuid): ByteArray? {
		val dir = cacheDir ?: return null
		val path = Path(dir, "$id.wav")
		if (!fileSystem.exists(path)) return null
		return runCatching {
			fileSystem.source(path).buffered().use { it.readByteArray() }
		}.onFailure { Napier.i("Cache read failed: ${it.message}") }.getOrNull()
	}

	fun putAudio(id: Uuid, bytes: ByteArray) {
		val dir = cacheDir ?: return
		val path = Path(dir, "$id.wav")
		runCatching {
			fileSystem.sink(path, append = false).buffered().use {
				it.write(bytes)
				it.flush()
			}
		}.onFailure { Napier.i("Cache write failed: ${it.message}") }
	}
}
