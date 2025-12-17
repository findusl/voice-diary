package de.lehrbaum.voiry

import ca.gosyer.appdirs.AppDirs

private val voiceDiaryAppDirs = AppDirs { appName = "voice-diary" }

fun voiceDiaryCacheDir(): String = voiceDiaryAppDirs.getUserCacheDir()

fun voiceDiaryDataDir(): String = voiceDiaryAppDirs.getUserDataDir()
