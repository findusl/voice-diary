package de.lehrbaum.voiry.audio

import java.io.IOException

actual fun isWhisperAvailable(): Boolean =
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
