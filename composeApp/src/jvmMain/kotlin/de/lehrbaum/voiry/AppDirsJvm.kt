package de.lehrbaum.voiry

import ca.gosyer.appdirs.AppDirs
import java.nio.file.Path

/** Resolves the user specific data directory for the application. */
fun voiceDiaryDataDir(): Path {
	val appDirs = AppDirs { appName = "voice-diary" }
	return Path.of(appDirs.getUserDataDir())
}
