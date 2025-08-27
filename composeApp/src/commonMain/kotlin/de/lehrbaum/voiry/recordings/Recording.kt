package de.lehrbaum.voiry.recordings

import kotlinx.io.Buffer

/** Simple model representing an audio recording entry. */
data class Recording(
	val id: String,
	val title: String,
	val transcript: String,
	val bytes: Buffer,
)
