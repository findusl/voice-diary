package de.lehrbaum.voiry.audio

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlinx.io.writeString

class WhisperCliTranscriberTest {
	@Test
	fun `invokes whisper-cli with language detection and parses json`() =
		runTest {
			val commands = mutableListOf<List<String>>()
			val runner: (List<String>) -> ProcessResult = { command ->
				commands += command
				when {
					command.contains("--detect-language") -> ProcessResult(0, "detected language: en")
					else -> {
						val fileIndex = command.indexOf("--file") + 1
						val wavPath = command[fileIndex]
						File("$wavPath.json").writeText(
							"{" +
								"\"transcription\":[{\"text\":\"Hello\"},{\"text\":\"World\"}]}",
						)
						ProcessResult(0, "")
					}
				}
			}
			val model = Files.createTempFile("model", ".bin")
			val manager = WhisperModelManager(model, model.toUri().toURL())
			val transcriber = WhisperCliTranscriber(modelManager = manager, processRunner = runner)
			transcriber.initialize()
			val buffer = Buffer().apply { writeString("dummy") }
			val prompt = "Meeting notes."
			val transcript = transcriber.transcribe(buffer, prompt)
			assertEquals("Hello World", transcript)
			assertTrue(commands[0].contains("--detect-language"))
			assertTrue(commands[0].none { it == "--initial-prompt" })
			val cmd = commands[1]
			assertTrue(cmd.contains("--file"))
			val path = cmd[cmd.indexOf("--file") + 1]
			assertTrue(path.endsWith(".wav"))
			assertTrue(cmd.containsAll(listOf("--language", "en")))
			val promptIndex = cmd.indexOf("--initial-prompt")
			assertTrue(promptIndex >= 0)
			assertEquals(prompt, cmd[promptIndex + 1])
		}
}
