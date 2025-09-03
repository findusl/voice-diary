package de.lehrbaum.voiry.audio

import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.runBlocking

class WhisperModelManagerTest {
	@Test
	fun deletesLeftoverPartFilesOnInitialize() =
		runBlocking {
			val tempDir = Files.createTempDirectory("whisper-test")
			val part = tempDir.resolve("leftover.part")
			Files.createFile(part)
			val modelPath = tempDir.resolve("model.bin")
			val remote = Files.createTempFile("remote-model", ".bin").also {
				Files.write(it, byteArrayOf(1))
			}
			val manager = WhisperModelManager(modelPath, remote.toUri().toURL())
			manager.initialize()
			assertFalse(part.exists(), "Leftover part file should be deleted")
			assertEquals(1f, manager.modelDownloadProgress.value)
		}

	@Test
	fun setsProgressToOneWhenModelAlreadyExists() =
		runBlocking {
			val tempDir = Files.createTempDirectory("whisper-test")
			val modelPath = tempDir.resolve("model.bin")
			Files.createFile(modelPath)
			val remote = Files.createTempFile("remote-model", ".bin")
			val manager = WhisperModelManager(modelPath, remote.toUri().toURL())
			manager.initialize()
			assertEquals(1f, manager.modelDownloadProgress.value)
		}
}
