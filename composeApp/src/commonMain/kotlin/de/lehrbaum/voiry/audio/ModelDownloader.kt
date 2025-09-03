package de.lehrbaum.voiry.audio

import kotlinx.coroutines.flow.StateFlow

/** Exposes progress of model downloads during initialization. */
interface ModelDownloader {
	/** Progress in range 0..1 or `null` if unknown. */
	val modelDownloadProgress: StateFlow<Float?>
}
