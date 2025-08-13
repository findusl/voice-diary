package de.lehrbaum.voiry.audio

actual val platformRecorder: Recorder by lazy { AudioRecorder() }
