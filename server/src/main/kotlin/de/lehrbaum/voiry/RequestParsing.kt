@file:OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)

package de.lehrbaum.voiry

import de.lehrbaum.voiry.api.v1.VoiceDiaryEntry
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
import io.ktor.utils.io.readRemaining
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json

internal suspend fun ApplicationCall.receiveEntryMultipart(): Pair<VoiceDiaryEntry, ByteArray>? {
	val multipart = receiveMultipart()
	var metadata: VoiceDiaryEntry? = null
	var audio: ByteArray? = null
	multipart.forEachPart { part ->
		try {
			when (part) {
				is PartData.FormItem -> if (part.name == "metadata") {
					metadata = runCatching {
						Json.decodeFromString<VoiceDiaryEntry>(part.value)
					}.getOrNull()
				}
				is PartData.FileItem -> if (part.name == "audio") {
					audio = part.provider().readRemaining().readByteArray()
				}
				else -> {}
			}
		} finally {
			part.release()
		}
	}
	val parsedMetadata = metadata
	val receivedAudio = audio
	return if (parsedMetadata != null && receivedAudio != null) {
		parsedMetadata to receivedAudio
	} else {
		null
	}
}

internal fun ApplicationCall.uuidParameter(name: String): Uuid? = parameters[name]?.let(Uuid::parseOrNull)
