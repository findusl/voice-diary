package de.lehrbaum.voicerecorder

import io.github.aakira.napier.Napier
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.CancellationException
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.TargetDataLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.io.Buffer
import kotlinx.io.asOutputStream

abstract class BaseAudioRecorder : AutoCloseable, CoroutineScope, Recorder {
	override val coroutineContext: Job = SupervisorJob()
	protected lateinit var line: TargetDataLine
	protected lateinit var format: AudioFormat
	private var recordingStream: ByteArrayOutputStream? = null
	private var isRecording = false
	private var _isAvailable = false
	override val isAvailable: Boolean
		get() = _isAvailable
	protected open val tag = "AudioRecorder"

	init {
		_isAvailable = initializeLineAndFormat()
	}

	protected abstract fun initializeLineAndFormat(): Boolean

	override fun startRecording() {
		isRecording = true
		if (!line.isOpen) {
			line.open(format)
		}
		line.start()
		recordingStream = ByteArrayOutputStream()
		Thread {
			try {
				val buffer = ByteArray(1024)
				recordingStream?.let { out ->
					while (isRecording) {
						val bytesRead = line.read(buffer, 0, buffer.size)
						if (bytesRead > 0 && isRecording) {
							out.write(buffer, 0, bytesRead)
						}
					}
				}
			} catch (e: IOException) {
				Napier.w("Exception while recording audio", e, tag = tag)
			} finally {
				line.close()
			}
		}.start()
	}

	override fun stopRecording(): Result<Buffer> {
		if (!isRecording) return Result.failure(IllegalStateException("AudioRecorder is not recording"))
		isRecording = false
		line.stop()
		line.flush()
		val buffer = recordingStream ?: return Result.failure(IllegalStateException("RecordingStream is null"))

		recordingStream = null
		return Result.success(convertToWavBuffer(buffer))
	}

	private fun convertToWavBuffer(rawBuffer: ByteArrayOutputStream): Buffer {
		val audioBytes = rawBuffer.toByteArray()
		val bais = ByteArrayInputStream(audioBytes)
		val audioInputStream = AudioInputStream(bais, format, audioBytes.size.toLong() / format.frameSize)
		val out = Buffer()
		AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, out.asOutputStream())
		return out
	}

	override fun close() {
		line.close()
		recordingStream?.close()
		recordingStream = null
		coroutineContext.cancel(CancellationException("AudioRecorder closed"))
	}
}
