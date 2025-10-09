package de.lehrbaum.voicerecorder

actual val platformRecorder: Recorder by lazy { AudioRecorder() }
