package de.lehrbaum.voiry.audio

import io.github.aakira.napier.Napier
import java.io.File
import kotlin.io.path.createTempFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val TAG = "WhisperCliTranscriber"

/** Desktop transcriber backed by the `whisper-cli` executable. */
class WhisperCliTranscriber(
	override val modelManager: WhisperModelManager = WhisperModelManager(),
	private val processRunner: (List<String>) -> Int = { command ->
		ProcessBuilder(command).start().waitFor()
	},
) : Transcriber {
	override suspend fun initialize() {
		modelManager.initialize()
	}

	override suspend fun transcribe(buffer: Buffer): String =
		withContext(Dispatchers.IO) {
			val tmp = createTempFile(prefix = "voice-diary", suffix = ".wav").toFile()
			val jsonFile = File(tmp.absolutePath + ".json")
			try {
				tmp.writeBytes(buffer.readByteArray())

				val command = listOf(
					"whisper-cli",
					"--model",
					modelManager.modelPath.toString(),
					"--file",
					tmp.absolutePath,
					"--output-json",
				)
				Napier.d("Running: ${command.joinToString(" ")}", tag = TAG)
				val exit = processRunner(command)
				if (exit != 0) {
					Napier.e(
						"whisper-cli exited $exit: ${command.joinToString(" ")}",
						tag = TAG,
					)
					throw RuntimeException("whisper-cli failed with exit code $exit")
				} else {
					Napier.i(
						"whisper-cli exited $exit: ${command.joinToString(" ")}",
						tag = TAG,
					)
				}

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
