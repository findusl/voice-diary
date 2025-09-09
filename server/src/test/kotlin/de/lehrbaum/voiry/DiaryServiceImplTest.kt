import de.lehrbaum.voiry.DiaryRepository
import de.lehrbaum.voiry.DiaryServiceImpl
import de.lehrbaum.voiry.api.v1.DiaryEvent
import de.lehrbaum.voiry.api.v1.TranscriptionStatus
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
class DiaryServiceImplTest {
	@Test
	fun `updateTranscription on missing entry emits no events`() =
		runBlocking {
			val repository = DiaryRepository(Files.createTempDirectory("serviceTest"))
			val service = DiaryServiceImpl.create(repository)
			val events = mutableListOf<DiaryEvent>()
			val job = launch { service.eventFlow().collect { events += it } }
			withTimeout(1_000) {
				while (events.isEmpty()) yield()
			}
			service.updateTranscription(
				Uuid.random(),
				"ignored",
				TranscriptionStatus.IN_PROGRESS,
				Clock.System.now(),
			)
			delay(100)
			assertEquals(listOf<DiaryEvent>(DiaryEvent.EntriesSnapshot(emptyList())), events)
			job.cancel()
		}
}
