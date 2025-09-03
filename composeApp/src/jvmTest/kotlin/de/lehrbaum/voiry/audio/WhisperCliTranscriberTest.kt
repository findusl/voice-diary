package de.lehrbaum.voiry.audio

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import kotlinx.io.writeString

class WhisperCliTranscriberTest {
	@Test
	fun `invokes whisper-cli with temp file and parses json`() =
		runBlocking {
			var capturedCommand: List<String>? = null
			val runner: (List<String>) -> Int = { command ->
				capturedCommand = command
				val fileIndex = command.indexOf("--file") + 1
				val wavPath = command[fileIndex]
				File("$wavPath.json").writeText(
					"{" +
						"\"transcription\":[{\"text\":\"Hello\"},{\"text\":\"World\"}]}",
				)
				0
			}
			val model = Files.createTempFile("model", ".bin")
			val manager = WhisperModelManager(model, model.toUri().toURL())
			val transcriber = WhisperCliTranscriber(modelManager = manager, processRunner = runner)
			transcriber.initialize()
			val buffer = Buffer().apply { writeString("dummy") }
			val transcript = transcriber.transcribe(buffer)
			assertEquals("Hello World", transcript)
			val cmd = capturedCommand ?: error("command not captured")
			assertTrue(cmd.contains("--file"))
			val path = cmd[cmd.indexOf("--file") + 1]
			assertTrue(path.endsWith(".wav"))
		}
}
