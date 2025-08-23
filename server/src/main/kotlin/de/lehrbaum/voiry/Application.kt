package de.lehrbaum.voiry

import de.lehrbaum.voiry.api.v1.DiaryEvent
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import kotlinx.serialization.json.Json

fun main() {
	embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
		.start(wait = true)
}

fun Application.module(eventProvider: DiaryEventProvider = DiaryService()) {
	install(ContentNegotiation) { json() }
	install(SSE)

	routing {
		route("/v1") {
			sse("/entries") {
				val json = Json
				eventProvider.eventFlow().collect { event: DiaryEvent ->
					val data = json.encodeToString(event)
					send(ServerSentEvent(data = data))
				}
			}
		}
	}
}

