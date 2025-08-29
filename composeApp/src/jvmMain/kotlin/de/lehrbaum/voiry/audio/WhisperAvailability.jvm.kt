package de.lehrbaum.voiry.audio

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual suspend fun isWhisperAvailable(): Boolean =
	withContext(Dispatchers.IO) {
		try {
			ProcessBuilder("whisper-cli", "--help")
				.redirectErrorStream(true)
				.start()
				.also {
					it.waitFor()
					it.destroy()
				}
			true
		} catch (e: IOException) {
			false
		}
	}
