package de.lehrbaum.voiry.ui

import de.lehrbaum.voiry.api.v1.DiaryClient
import de.lehrbaum.voiry.api.v1.TranscriptionStatus
import de.lehrbaum.voiry.api.v1.VoiceDiaryEntry
import de.lehrbaum.voiry.audio.Player
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class, ExperimentalCoroutinesApi::class)
class EntryDetailViewModelTest : MainDispatcherTest by MainDispatcherRule() {
	@Test
	fun `does not download audio until needed`() =
		runTest {
			val id = Uuid.random()
			val entry = VoiceDiaryEntry(
				id = id,
				title = "title",
				recordedAt = Instant.fromEpochMilliseconds(0),
				duration = Duration.ZERO,
				transcriptionStatus = TranscriptionStatus.NONE,
			)
			val entryFlow = MutableStateFlow(entry)
			var getAudioCalls = 0
			val diaryClient =
				mock<DiaryClient> {
					every { entryFlow(id) } returns entryFlow
					everySuspend { getAudio(id) }
						.calls { _ ->
							getAudioCalls++
							byteArrayOf(1)
						}
				}
			val player = mock<Player>(mode = MockMode.autoUnit)
			every { player.isAvailable } returns true
			EntryDetailViewModel(diaryClient, id, player, null)
			advanceUntilIdle()
			assertEquals(0, getAudioCalls)
		}

	@Test
	fun `downloads audio on playback`() =
		runTest {
			val id = Uuid.random()
			val entry = VoiceDiaryEntry(
				id = id,
				title = "title",
				recordedAt = Instant.fromEpochMilliseconds(0),
				duration = Duration.ZERO,
				transcriptionStatus = TranscriptionStatus.NONE,
			)
			val audio = byteArrayOf(1)
			val entryFlow = MutableStateFlow(entry)
			var getAudioCalls = 0
			val diaryClient =
				mock<DiaryClient> {
					every { entryFlow(id) } returns entryFlow
					everySuspend { getAudio(id) }
						.calls { _ ->
							getAudioCalls++
							audio
						}
				}
			val player = mock<Player>(mode = MockMode.autoUnit)
			every { player.isAvailable } returns true
			val viewModel = EntryDetailViewModel(diaryClient, id, player, null)
			advanceUntilIdle()
			viewModel.togglePlayback()
			advanceUntilIdle()
			assertEquals(1, getAudioCalls)
			verify { player.play(audio) }
		}
}
