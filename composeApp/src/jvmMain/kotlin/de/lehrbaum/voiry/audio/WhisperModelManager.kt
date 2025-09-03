package de.lehrbaum.voiry.audio

import de.lehrbaum.voiry.voiceDiaryDataDir
import io.github.aakira.napier.Napier
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.outputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

private const val TAG = "WhisperModelManager"

/** Handles the Whisper model file and cleans up partial downloads. */
class WhisperModelManager(
	val modelPath: Path = voiceDiaryDataDir().resolve("whisper-models/ggml-large-v3-turbo.bin"),
	private val modelUrl: URL = MODEL_URL,
) : ModelDownloader {
	private var initialized = false
	private val _modelDownloadProgress = MutableStateFlow<Float?>(null)
	override val modelDownloadProgress: StateFlow<Float?> = _modelDownloadProgress.asStateFlow()

	suspend fun initialize() =
		withContext(Dispatchers.IO) {
			if (initialized) return@withContext

			val modelDir = modelPath.parent ?: error("Model path has no parent")
			modelDir.createDirectories()
			cleanupLeftoverPartFiles(modelDir)
			if (modelPath.exists()) {
				_modelDownloadProgress.value = 1f
			} else {
				downloadModel(modelDir)
			}
			initialized = true
		}

	private fun cleanupLeftoverPartFiles(modelDir: Path) {
		modelDir.listDirectoryEntries("*.part").forEach {
			runCatching { it.deleteIfExists() }
		}
	}

	private fun downloadModel(modelDir: Path) {
		Napier.i("Downloading Whisper model to ${modelPath.toAbsolutePath()}", tag = TAG)
		val tmp = createTempFile(directory = modelDir, prefix = "whisper-", suffix = ".part")
		tmp.toFile().deleteOnExit()
		val connection = modelUrl.openConnection()
		val total = connection.contentLengthLong.takeIf { it > 0 }
		_modelDownloadProgress.value = 0f
		connection.getInputStream().use { input ->
			tmp.outputStream().use { output ->
				val buf = ByteArray(DEFAULT_BUFFER_SIZE)
				var downloaded = 0L
				var read: Int
				while (input.read(buf).also { read = it } >= 0) {
					output.write(buf, 0, read)
					downloaded += read
					total?.let { _modelDownloadProgress.value = downloaded.toFloat() / it }
				}
			}
		}
		Files.move(tmp, modelPath, StandardCopyOption.ATOMIC_MOVE)
		_modelDownloadProgress.value = 1f
	}

	companion object {
		private val MODEL_URL = URI("https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo.bin").toURL()
		private const val DEFAULT_BUFFER_SIZE = 8 * 1024
	}
}
