package de.lehrbaum.voiry

import de.lehrbaum.voiry.api.v1.DiaryEvent
import kotlinx.coroutines.flow.Flow

interface DiaryEventProvider {
	fun eventFlow(): Flow<DiaryEvent>
}
