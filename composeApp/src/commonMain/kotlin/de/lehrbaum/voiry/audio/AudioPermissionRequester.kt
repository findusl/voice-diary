package de.lehrbaum.voiry.audio

/** Requests microphone access and reports the result to the caller. */
fun interface AudioPermissionRequester {
	fun requestPermission(onResult: (Boolean) -> Unit)
}
