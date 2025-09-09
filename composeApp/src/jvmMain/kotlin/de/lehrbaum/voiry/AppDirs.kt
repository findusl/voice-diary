package de.lehrbaum.voiry

import ca.gosyer.appdirs.AppDirs
import java.nio.file.Path

/** Resolves the user specific data directory for the application. */
fun voiceDiaryDataDir(): Path {
	val env = System.getenv("VOICE_DIARY_DATA_PATH")
	return if (!env.isNullOrBlank()) {
		Path.of(env)
	} else {
		val appDirs = AppDirs { appName = "voice-diary" }
		Path.of(appDirs.getUserDataDir())
	}
}

actual fun voiceDiaryCacheDir(): String {
	val env = System.getenv("VOICE_DIARY_CACHE_PATH")
	return if (!env.isNullOrBlank()) {
		env
	} else {
		val appDirs = AppDirs { appName = "voice-diary" }
		appDirs.getUserCacheDir()
	}
}
