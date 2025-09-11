package de.lehrbaum.voiry

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
class HealthEndpointTest {
	@Test
	fun `GET health returns OK`() =
		runTest {
			val service = DiaryServiceImpl.create(DiaryRepository(Files.createTempDirectory("healthTest")))
			testApplication {
				application { module(service) }
				val response = client.get("/health")
				assertEquals(HttpStatusCode.OK, response.status)
			}
		}
}
