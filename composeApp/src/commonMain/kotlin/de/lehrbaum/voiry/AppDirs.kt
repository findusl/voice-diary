package de.lehrbaum.voiry

import ca.gosyer.appdirs.AppDirs

fun voiceDiaryCacheDir(): String {
	val appDirs = AppDirs { appName = "voice-diary" }
	return appDirs.getUserCacheDir()
}
