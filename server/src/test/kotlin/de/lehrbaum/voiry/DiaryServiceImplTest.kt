import de.lehrbaum.voiry.DiaryRepository
import de.lehrbaum.voiry.DiaryServiceImpl
import de.lehrbaum.voiry.api.v1.DiaryEvent
import de.lehrbaum.voiry.api.v1.TranscriptionStatus
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
class DiaryServiceImplTest {
	@Test
	fun `updateTranscription on missing entry emits no events`() =
		runBlocking {
			val repository = DiaryRepository(Files.createTempDirectory("serviceTest"))
			val service = DiaryServiceImpl.create(repository)
			val channel = service.eventFlow().produceIn(this)
			val events = mutableListOf<DiaryEvent>()
			events += channel.receive()
			service.updateTranscription(
				Uuid.random(),
				"ignored",
				TranscriptionStatus.IN_PROGRESS,
				Clock.System.now(),
			)
			delay(100)
			assertTrue(channel.tryReceive().isFailure)
			assertEquals(listOf<DiaryEvent>(DiaryEvent.EntriesSnapshot(emptyList())), events)
			channel.cancel()
		}

	@Test
	fun `deleteEntry on missing entry emits no events`() =
		runBlocking {
			val repository = DiaryRepository(Files.createTempDirectory("deleteEntryTest"))
			val service = DiaryServiceImpl.create(repository)
			val channel = service.eventFlow().produceIn(this)
			val events = mutableListOf<DiaryEvent>()
			events += channel.receive()
			service.deleteEntry(Uuid.random())
			delay(100)
			assertTrue(channel.tryReceive().isFailure)
			assertEquals(listOf<DiaryEvent>(DiaryEvent.EntriesSnapshot(emptyList())), events)
			channel.cancel()
		}
}
