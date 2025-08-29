package de.lehrbaum.voiry.audio

import java.io.File
import kotlin.io.path.createTempFile
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Desktop transcriber backed by the `whisper-cli` executable. */
class WhisperCliTranscriber(
	private val modelPath: String = "whisper-models/ggml-large-v3-turbo.bin",
) : Transcriber {
	override suspend fun transcribe(buffer: Buffer): String {
		// Write buffer to temporary wav file
		val tmp = createTempFile(prefix = "voice-diary", suffix = ".wav").toFile()
		tmp.writeBytes(buffer.readByteArray())

		val command = listOf(
			"whisper-cli",
			"--model",
			modelPath,
			"--file",
			tmp.absolutePath,
			"--output-json",
		)
		val process = ProcessBuilder(command).start()
		val exit = process.waitFor()
		if (exit != 0) throw RuntimeException("whisper-cli failed with exit code $exit")

		val jsonFile = File(tmp.absolutePath + ".json")
		if (!jsonFile.exists()) throw RuntimeException("JSON output file not found: ${jsonFile.absolutePath}")

		val json = Json { ignoreUnknownKeys = true }
		val result = json.decodeFromString<WhisperResult>(jsonFile.readText())
		val transcript = result.transcription.joinToString(" ") { it.text.trim() }

		tmp.delete()
		jsonFile.delete()

		return transcript
	}

	@Serializable
	private data class WhisperResult(val transcription: List<Segment>)

	@Serializable
	private data class Segment(val text: String)
}
