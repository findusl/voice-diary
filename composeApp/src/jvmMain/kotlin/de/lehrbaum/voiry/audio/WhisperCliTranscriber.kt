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

data class ProcessResult(val exitCode: Int, val output: String)

private const val TAG = "WhisperCliTranscriber"

/** Desktop transcriber backed by the `whisper-cli` executable. */
class WhisperCliTranscriber(
	override val modelManager: WhisperModelManager = WhisperModelManager(),
	private val processRunner: (List<String>) -> ProcessResult = { command ->
		val process = ProcessBuilder(command).redirectErrorStream(true).start()
		val output = buildString {
			process.inputStream.bufferedReader().useLines { lines ->
				lines.forEach { line ->
					Napier.v(line, tag = TAG)
					appendLine(line)
				}
			}
		}
		val exit = process.waitFor()
		ProcessResult(exit, output)
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

				val detectCommand = listOf(
					"whisper-cli",
					"--model",
					modelManager.modelPath.toString(),
					"--file",
					tmp.absolutePath,
					"--detect-language",
				)
				Napier.d("Running: ${detectCommand.joinToString(" ")}", tag = TAG)
				val detectResult = processRunner(detectCommand)
				val detected = parseLanguage(detectResult.output)
				val language = when (detected) {
					"de" -> "de"
					else -> "en"
				}
				if (detectResult.exitCode != 0) {
					Napier.e(
						"whisper-cli exited ${detectResult.exitCode}: ${detectCommand.joinToString(" ")}",
						tag = TAG,
					)
					throw RuntimeException("whisper-cli failed with exit code ${detectResult.exitCode}")
				}

				val command = listOf(
					"whisper-cli",
					"--model",
					modelManager.modelPath.toString(),
					"--file",
					tmp.absolutePath,
					"--language",
					language,
					"--output-json",
				)
				Napier.d("Running: ${command.joinToString(" ")}", tag = TAG)
				val exit = processRunner(command)
				if (exit.exitCode != 0) {
					Napier.e(
						"whisper-cli exited ${exit.exitCode}: ${command.joinToString(" ")}",
						tag = TAG,
					)
					throw RuntimeException("whisper-cli failed with exit code ${exit.exitCode}")
				} else {
					Napier.i(
						"whisper-cli exited ${exit.exitCode}: ${command.joinToString(" ")}",
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

	private fun parseLanguage(output: String): String? {
		val regex = Regex("language:\\s*([a-zA-Z-]+)")
		return regex.find(output)?.groupValues?.get(1)
	}
}
