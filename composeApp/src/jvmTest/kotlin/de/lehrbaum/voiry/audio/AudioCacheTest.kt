package de.lehrbaum.voiry.audio

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class AudioCacheTest {
	@Test
	fun storeAndRetrieve() {
		val dir = Files.createTempDirectory("audioCacheTest").toString()
		val cache = AudioCache(dir)
		val id = Uuid.random()
		val bytes = byteArrayOf(1, 2, 3)
		cache.putAudio(id, bytes)
		val result = cache.getAudio(id)
		assertContentEquals(bytes, result)
	}

	@Test
	fun returnsNullWhenMissing() {
		val dir = Files.createTempDirectory("audioCacheTestMissing").toString()
		val cache = AudioCache(dir)
		val result = cache.getAudio(Uuid.random())
		assertNull(result)
	}
}
