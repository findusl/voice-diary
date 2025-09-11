package de.lehrbaum.voiry.ui

import de.lehrbaum.voiry.api.v1.DiaryClient
import de.lehrbaum.voiry.api.v1.TranscriptionStatus
import de.lehrbaum.voiry.api.v1.UpdateTranscriptionRequest
import de.lehrbaum.voiry.api.v1.VoiceDiaryEntry
import de.lehrbaum.voiry.audio.AudioCache
import de.lehrbaum.voiry.audio.Player
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class, ExperimentalCoroutinesApi::class)
class EntryDetailViewModelTest : MainDispatcherTest by MainDispatcherRule() {
	@Test
	fun `does not download audio until needed`() =
		runTest {
			val id = Uuid.random()
			val diaryClient = FakeDiaryClient(id)
			EntryDetailViewModel(diaryClient, id, FakePlayer(), null)
			advanceUntilIdle()
			assertEquals(0, diaryClient.getAudioCalls)
		}

	@Test
	fun `downloads audio on playback`() =
		runTest {
			val id = Uuid.random()
			val diaryClient = FakeDiaryClient(id)
			val player = FakePlayer()
			val viewModel = EntryDetailViewModel(diaryClient, id, player, null)
			advanceUntilIdle()
			viewModel.togglePlayback()
			advanceUntilIdle()
			assertEquals(1, diaryClient.getAudioCalls)
			assertEquals(1, player.playCalls)
		}

	private class FakeDiaryClient(id: Uuid) : DiaryClient("test", audioCache = AudioCache(".")) {
		private val entry = VoiceDiaryEntry(
			id = id,
			title = "title",
			recordedAt = Instant.fromEpochMilliseconds(0),
			duration = Duration.ZERO,
			transcriptionStatus = TranscriptionStatus.NONE,
		)
		var getAudioCalls = 0
		override val entries: StateFlow<PersistentList<VoiceDiaryEntry>> =
			MutableStateFlow(persistentListOf(entry))

		override fun entryFlow(id: Uuid): StateFlow<VoiceDiaryEntry?> = MutableStateFlow(entry)

		override suspend fun getAudio(id: Uuid): ByteArray {
			getAudioCalls++
			return byteArrayOf(1)
		}

		override suspend fun updateTranscription(id: Uuid, request: UpdateTranscriptionRequest) {}
	}

	private class FakePlayer : Player {
		var playCalls = 0
		override val isAvailable: Boolean = true

		override fun play(audio: ByteArray) {
			playCalls++
		}

		override fun stop() {}

		override fun close() {}
	}
}
