@file:OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)

package de.lehrbaum.voiry

import de.lehrbaum.voiry.api.v1.DiaryEvent
import de.lehrbaum.voiry.api.v1.UpdateTranscriptionRequest
import de.lehrbaum.voiry.api.v1.VoiceDiaryEntry
import io.github.aakira.napier.Napier
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.forEachPart
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import io.ktor.utils.io.readRemaining
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json

fun main() {
	Napier.base(Slf4jAntilog())
	Napier.i("Starting server")
	embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
		.start(wait = true)
}

fun Application.module(service: DiaryService = runBlocking { DiaryServiceImpl.create() }) {
	install(ContentNegotiation) { json() }
	install(SSE)

	routing {
		route("/v1") {
			sse("/entries") {
				val json = Json
				service.eventFlow().collect { event: DiaryEvent ->
					val data = json.encodeToString(event)
					send(ServerSentEvent(data = data))
				}
			}

			post("/entries") {
				val multipart = call.receiveMultipart()
				var metadata: VoiceDiaryEntry? = null
				var audio: ByteArray? = null
				multipart.forEachPart { part ->
					when (part) {
						is io.ktor.http.content.PartData.FormItem -> if (part.name == "metadata") {
							metadata = Json.decodeFromString<VoiceDiaryEntry>(part.value)
						}
						is io.ktor.http.content.PartData.FileItem -> if (part.name == "audio") {
							audio = part.provider().readRemaining().readByteArray()
						}
						else -> {}
					}
				}
				if (metadata == null || audio == null) {
					call.respond(HttpStatusCode.BadRequest)
				} else {
					service.addEntry(metadata, audio)
					call.respond(metadata)
				}
			}

			put("/entries/{id}/transcription") {
				val idParam = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
				val id = Uuid.parse(idParam)
				val req = call.receive<UpdateTranscriptionRequest>()
				service.updateTranscription(id, req.transcriptionText, req.transcriptionStatus, req.transcriptionUpdatedAt)
				call.respond(HttpStatusCode.OK)
			}

			delete("/entries/{id}") {
				val idParam = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
				val id = Uuid.parse(idParam)
				service.deleteEntry(id)
				call.respond(HttpStatusCode.NoContent)
			}

			get("/entries/{id}/audio") {
				val idParam = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
				val id = Uuid.parse(idParam)
				val audio = service.getAudio(id)
				if (audio == null) {
					call.respond(HttpStatusCode.NotFound)
				} else {
					call.respondBytes(audio, ContentType("audio", "wav"))
				}
			}
		}
	}
}
