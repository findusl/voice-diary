import de.lehrbaum.voiry.DiaryRepository
import de.lehrbaum.voiry.api.v1.TranscriptionStatus
import de.lehrbaum.voiry.api.v1.VoiceDiaryEntry
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking

private const val TEST_TRANSCRIPTION = "text"

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
class DiaryRepositoryTest {
	@Test
	fun `add update delete persists correctly`() =
		runBlocking {
			val dir = Files.createTempDirectory("diaryRepoTest")
			val repository = DiaryRepository(dir)
			val entry = VoiceDiaryEntry(
				id = Uuid.random(),
				title = "title",
				recordedAt = Clock.System.now(),
				duration = 1.seconds,
				transcriptionStatus = TranscriptionStatus.NONE,
			)
			val expectedAudio = byteArrayOf(1, 2, 3)
			repository.add(entry, expectedAudio)
			assertContentEquals(expectedAudio, repository.getAudio(entry.id))
			val updatedAt = Clock.System.now()
			repository.updateTranscription(entry.id, TEST_TRANSCRIPTION, TranscriptionStatus.IN_PROGRESS, updatedAt)
			val fromDb = repository.getAll().single()
			assertEquals(TEST_TRANSCRIPTION, fromDb.transcriptionText)
			repository.delete(entry.id)
			assertNull(repository.getAudio(entry.id))
		}
}
