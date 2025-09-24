package de.lehrbaum.voiry.audio

import androidx.compose.runtime.Stable
import kotlinx.io.Buffer

/** Abstraction for turning audio into text. */
@Stable
interface Transcriber {
	/** Optional manager exposing model download progress. */
	val modelManager: ModelDownloader? get() = null

	/** Performs one-time setup. */
	suspend fun initialize() = Unit

	/**
	 * Transcribes the given [buffer] and returns plain text.
	 * Implementations may suspend while invoking platform services.
	 *
	 * @param initialPrompt Optional text prompt that can steer the transcription.
	 */
	suspend fun transcribe(buffer: Buffer, initialPrompt: String? = null): String
}
