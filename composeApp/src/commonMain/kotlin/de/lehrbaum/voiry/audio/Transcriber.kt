package de.lehrbaum.voiry.audio

import androidx.compose.runtime.Stable
import kotlinx.io.Buffer

/** Abstraction for turning audio into text. */
@Stable
interface Transcriber {
	/**
	 * Transcribes the given [buffer] and returns plain text.
	 * Implementations may suspend while invoking platform services.
	 */
	suspend fun transcribe(buffer: Buffer): String
}
