package de.lehrbaum.voiry.audio

import java.io.File

actual fun tempCacheDir(): String {
	val dir = File.createTempFile("voice-diary", "")
	dir.delete()
	dir.mkdir()
	Runtime.getRuntime().addShutdownHook(Thread { dir.deleteRecursively() })
	return dir.absolutePath
}
