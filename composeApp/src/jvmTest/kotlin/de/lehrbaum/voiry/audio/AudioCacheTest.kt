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
		AudioCache.setBaseDirForTest(dir)
		val id = Uuid.random()
		val bytes = byteArrayOf(1, 2, 3)
		AudioCache.putAudio(id, bytes)
		val result = AudioCache.getAudio(id)
		assertContentEquals(bytes, result)
	}

	@Test
	fun returnsNullWhenMissing() {
		val dir = Files.createTempDirectory("audioCacheTestMissing").toString()
		AudioCache.setBaseDirForTest(dir)
		val result = AudioCache.getAudio(Uuid.random())
		assertNull(result)
	}
}
