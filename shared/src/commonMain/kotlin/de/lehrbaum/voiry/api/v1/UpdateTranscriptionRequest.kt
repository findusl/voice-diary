@file:OptIn(ExperimentalTime::class)

package de.lehrbaum.voiry.api.v1

import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class UpdateTranscriptionRequest(
	val transcriptionText: String?,
	val transcriptionStatus: TranscriptionStatus,
	val transcriptionUpdatedAt: Instant?,
)
