package de.lehrbaum.voiry.audio

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
expect object AudioCache {
	fun getAudio(id: Uuid): ByteArray?

	fun putAudio(id: Uuid, bytes: ByteArray)

	fun cacheRecording(bytes: ByteArray)
}
