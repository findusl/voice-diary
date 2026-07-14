@file:OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)

package de.lehrbaum.voiry

import de.lehrbaum.voiry.api.v1.DiaryEvent
import de.lehrbaum.voiry.api.v1.UpdateTranscriptionRequest
import io.github.aakira.napier.Napier
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

private const val SERVER_PORT = 8888

fun main() {
	Napier.base(Slf4jAntilog())
	Napier.i("Starting server")
	embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
		.start(wait = true)
}

fun Application.module(service: DiaryService = runBlocking { DiaryServiceImpl.create() }) {
	install(ContentNegotiation) { json() }
	install(SSE)

	configureHealthCheck()
	configureV1Routes(service)
}

private fun Application.configureHealthCheck() {
	routing {
		get("/health") { call.respond(HttpStatusCode.OK) }
	}
}

private fun Application.configureV1Routes(service: DiaryService) {
	routing {
		route("/v1") {
			entriesEvents(service)
			postEntry(service)
			updateTranscription(service)
			deleteEntry(service)
			entryAudio(service)
		}
	}
}

private fun Route.entriesEvents(service: DiaryService) {
	sse("/entries") {
		val json = Json
		service.eventFlow().collect { event: DiaryEvent ->
			val data = json.encodeToString(event)
			send(ServerSentEvent(data = data))
		}
	}
}

private fun Route.postEntry(service: DiaryService) {
	post("/entries") {
		val entry = call.receiveEntryMultipart()
		if (entry == null) {
			call.respond(HttpStatusCode.BadRequest)
		} else {
			val (metadata, audio) = entry
			try {
				service.addEntry(metadata, audio)
				call.respond(metadata)
			} catch (_: EntryConflictException) {
				call.respond(HttpStatusCode.Conflict)
			}
		}
	}
}

private fun Route.updateTranscription(service: DiaryService) {
	put("/entries/{id}/transcription") {
		val id = call.uuidParameter("id") ?: return@put call.respond(HttpStatusCode.BadRequest)
		val req = call.receive<UpdateTranscriptionRequest>()
		service.updateTranscription(id, req.transcriptionText, req.transcriptionStatus, req.transcriptionUpdatedAt)
		call.respond(HttpStatusCode.OK)
	}
}

private fun Route.deleteEntry(service: DiaryService) {
	delete("/entries/{id}") {
		val id = call.uuidParameter("id") ?: return@delete call.respond(HttpStatusCode.BadRequest)
		service.deleteEntry(id)
		call.respond(HttpStatusCode.NoContent)
	}
}

private fun Route.entryAudio(service: DiaryService) {
	get("/entries/{id}/audio") {
		val id = call.uuidParameter("id") ?: return@get call.respond(HttpStatusCode.BadRequest)
		val audio = service.getAudio(id)
		if (audio == null) {
			call.respond(HttpStatusCode.NotFound)
		} else {
			call.respondBytes(audio, ContentType("audio", "wav"))
		}
	}
}
