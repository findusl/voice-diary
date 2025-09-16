package de.lehrbaum.voiry

import ca.gosyer.appdirs.AppDirs
import yairm210.purity.annotations.Readonly

private val voiceDiaryAppDirs = AppDirs { appName = "voice-diary" }

@Readonly
fun voiceDiaryCacheDir(): String = voiceDiaryAppDirs.getUserCacheDir()

@Readonly
fun voiceDiaryDataDir(): String = voiceDiaryAppDirs.getUserDataDir()
