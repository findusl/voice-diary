package de.lehrbaum.voiry.audio

import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

class WhisperModelManagerTest {
	@Test
	fun deletesLeftoverPartFilesOnInitialize() =
		runTest {
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
		runTest {
			val tempDir = Files.createTempDirectory("whisper-test")
			val modelPath = tempDir.resolve("model.bin")
			Files.createFile(modelPath)
			val remote = Files.createTempFile("remote-model", ".bin")
			val manager = WhisperModelManager(modelPath, remote.toUri().toURL())
			manager.initialize()
			assertEquals(1f, manager.modelDownloadProgress.value)
		}

	@Test
	fun failedDownloadCanBeRetried() =
		runTest {
			val tempDir = Files.createTempDirectory("whisper-retry-test")
			val modelPath = tempDir.resolve("model.bin")
			val attempts = AtomicInteger()
			val remote = testUrl { _ ->
				if (attempts.incrementAndGet() == 1) throw IOException("download failed")
				byteArrayOf(1, 2, 3).inputStream()
			}
			val manager = WhisperModelManager(modelPath, remote)

			assertFailsWith<IOException> { manager.initialize() }

			assertNull(manager.modelDownloadProgress.value)
			assertTrue(tempDir.listDirectoryEntries("*.part").isEmpty())

			manager.initialize()

			assertEquals(2, attempts.get())
			assertEquals(1f, manager.modelDownloadProgress.value)
			assertTrue(modelPath.exists())
		}

	@Test
	fun concurrentInitializationDownloadsOnlyOnce() =
		runTest {
			val tempDir = Files.createTempDirectory("whisper-concurrent-test")
			val modelPath = tempDir.resolve("model.bin")
			val opens = AtomicInteger()
			val firstDownloadStarted = CountDownLatch(1)
			val releaseDownload = CountDownLatch(1)
			val secondDownloadStarted = CountDownLatch(1)
			val remote = testUrl { _ ->
				val openCount = opens.incrementAndGet()
				firstDownloadStarted.countDown()
				if (openCount > 1) secondDownloadStarted.countDown()
				check(releaseDownload.await(5, TimeUnit.SECONDS))
				byteArrayOf(1, 2, 3).inputStream()
			}
			val manager = WhisperModelManager(modelPath, remote)

			val first = async(Dispatchers.Default) { manager.initialize() }
			assertTrue(withContext(Dispatchers.IO) { firstDownloadStarted.await(5, TimeUnit.SECONDS) })
			val second = async(Dispatchers.Default) { manager.initialize() }
			val raced = withContext(Dispatchers.IO) {
				secondDownloadStarted.await(250, TimeUnit.MILLISECONDS)
			}
			releaseDownload.countDown()
			awaitAll(first, second)

			assertFalse(raced, "A second download started while initialization was already in progress")
			assertEquals(1, opens.get())
		}

	private fun testUrl(openInput: (URL) -> InputStream): URL =
		URL.of(
			URI("test://whisper-model"),
			object : URLStreamHandler() {
				override fun openConnection(url: URL): URLConnection =
					object : URLConnection(url) {
						override fun connect() = Unit

						override fun getContentLengthLong(): Long = 3

						override fun getInputStream(): InputStream = openInput(url)
					}
			},
		)
}
