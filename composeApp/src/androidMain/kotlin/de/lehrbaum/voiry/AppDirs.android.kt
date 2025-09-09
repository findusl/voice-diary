package de.lehrbaum.voiry

import ca.gosyer.appdirs.AppDirs

actual fun voiceDiaryCacheDir(): String {
	val env = System.getenv("VOICE_DIARY_CACHE_PATH")
	return if (!env.isNullOrBlank()) {
		env
	} else {
		val appDirs = AppDirs { appName = "voice-diary" }
		appDirs.getUserCacheDir()
	}
}
