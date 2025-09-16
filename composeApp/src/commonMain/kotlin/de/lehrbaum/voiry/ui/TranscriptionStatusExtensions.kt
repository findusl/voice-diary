package de.lehrbaum.voiry.ui

import de.lehrbaum.voiry.api.v1.TranscriptionStatus
import yairm210.purity.annotations.Pure

@Pure
fun TranscriptionStatus.displayName(): String =
	when (this) {
		TranscriptionStatus.NONE -> "Not yet transcribed"
		TranscriptionStatus.IN_PROGRESS -> "Transcribing"
		TranscriptionStatus.DONE -> "Transcribed"
		TranscriptionStatus.FAILED -> "Transcription failed"
	}
