package de.lehrbaum.voiry.audio

import java.io.File
import kotlin.io.path.createTempFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Desktop transcriber backed by the `whisper-cli` executable. */
class WhisperCliTranscriber(
	private val modelPath: String = "whisper-models/ggml-large-v3-turbo.bin",
	private val processRunner: (List<String>) -> Int = { command ->
		ProcessBuilder(command).start().waitFor()
	},
) : Transcriber {
	override suspend fun transcribe(buffer: Buffer): String =
		withContext(Dispatchers.IO) {
			val tmp = createTempFile(prefix = "voice-diary", suffix = ".wav").toFile()
			val jsonFile = File(tmp.absolutePath + ".json")
			try {
				tmp.writeBytes(buffer.readByteArray())

				val command = listOf(
					"whisper-cli",
					"--model",
					modelPath,
					"--file",
					tmp.absolutePath,
					"--output-json",
				)
				val exit = processRunner(command)
				if (exit != 0) throw RuntimeException("whisper-cli failed with exit code $exit")

				if (!jsonFile.exists()) throw RuntimeException("JSON output file not found: ${jsonFile.absolutePath}")

				val json = Json { ignoreUnknownKeys = true }
				val result = json.decodeFromString<WhisperResult>(jsonFile.readText())
				result.transcription.joinToString(" ") { it.text.trim() }
			} finally {
				tmp.delete()
				jsonFile.delete()
			}
		}

	@Serializable
	private data class WhisperResult(val transcription: List<Segment>)

	@Serializable
	private data class Segment(val text: String)
}
