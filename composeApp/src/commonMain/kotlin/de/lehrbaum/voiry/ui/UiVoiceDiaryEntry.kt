@file:OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)

package de.lehrbaum.voiry.ui

import androidx.compose.runtime.Immutable
import de.lehrbaum.voiry.api.v1.TranscriptionStatus
import de.lehrbaum.voiry.api.v1.VoiceDiaryEntry
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Immutable
data class UiVoiceDiaryEntry(
	val id: Uuid,
	val title: String,
	val recordedAt: Instant,
	val duration: Duration,
	val transcriptionText: String?,
	val transcriptionStatus: TranscriptionStatus,
	val transcriptionUpdatedAt: Instant?,
)

fun VoiceDiaryEntry.toUi() =
	UiVoiceDiaryEntry(
		id = id,
		title = title,
		recordedAt = recordedAt,
		duration = duration,
		transcriptionText = transcriptionText,
		transcriptionStatus = transcriptionStatus,
		transcriptionUpdatedAt = transcriptionUpdatedAt,
	)
