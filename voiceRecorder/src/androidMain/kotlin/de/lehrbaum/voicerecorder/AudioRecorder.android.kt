package de.lehrbaum.voicerecorder

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import io.github.aakira.napier.Napier
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.io.Buffer
import kotlinx.io.asOutputStream

class AudioRecorder : Recorder {
	private var audioRecord: AudioRecord? = null
	private var bufferSizeInBytes: Int = 0
	private var sampleRate: Int = 44100
	private val channelConfig = AudioFormat.CHANNEL_IN_MONO
	private val audioEncoding = AudioFormat.ENCODING_PCM_16BIT
	private var recordingThread: Thread? = null
	private var isRecording = AtomicBoolean(false)
	private var recordingStream: ByteArrayOutputStream? = null

	override val isAvailable: Boolean

	init {
		// Determine supported sample rate and buffer size
		val rates = intArrayOf(44100, 48000, 22050, 16000, 11025)
		var available = false
		for (rate in rates) {
			val min = AudioRecord.getMinBufferSize(rate, channelConfig, audioEncoding)
			if (min > 0) {
				sampleRate = rate
				bufferSizeInBytes = min
				available = true
				break
			}
		}
		isAvailable = available
	}

	override fun startRecording() {
		if (!isAvailable) throw IllegalStateException("Audio recorder not available")
		if (isRecording.get()) throw IllegalStateException("Already recording")

		val minBuffer = if (bufferSizeInBytes > 0) {
			bufferSizeInBytes
		} else {
			AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioEncoding).coerceAtLeast(4096)
		}
		try {
			audioRecord = AudioRecord(
				MediaRecorder.AudioSource.MIC,
				sampleRate,
				channelConfig,
				audioEncoding,
				minBuffer,
			)
		} catch (se: SecurityException) {
			throw IllegalStateException("Failed to init AudioRecord. Microphone permission may be missing.", se)
		}
		val ar = audioRecord ?: throw IllegalStateException("Failed to init AudioRecord")
		recordingStream = ByteArrayOutputStream()
		isRecording.set(true)
		try {
			ar.startRecording()
		} catch (se: SecurityException) {
			isRecording.set(false)
			try {
				ar.release()
			} catch (_: Throwable) {
			}
			audioRecord = null
			recordingStream = null
			throw IllegalStateException("Failed to start recording. Microphone permission may be missing.", se)
		} catch (t: Throwable) {
			isRecording.set(false)
			try {
				ar.release()
			} catch (_: Throwable) {
			}
			audioRecord = null
			recordingStream = null
			throw IllegalStateException("Failed to start recording.", t)
		}

		recordingThread = Thread {
			try {
				val buffer = ByteArray(minBuffer)
				while (isRecording.get()) {
					val read = ar.read(buffer, 0, buffer.size)
					if (read > 0) {
						recordingStream?.write(buffer, 0, read)
					}
				}
			} catch (t: Throwable) {
				Napier.w("Exception while recording audio", t)
			} finally {
				try {
					ar.stop()
				} catch (_: Throwable) {
				}
				try {
					ar.release()
				} catch (_: Throwable) {
				}
			}
		}.also { it.start() }
	}

	override fun stopRecording(): Result<Buffer> {
		if (!isRecording.get()) return Result.failure(IllegalStateException("AudioRecorder is not recording"))
		isRecording.set(false)
		try {
			recordingThread?.join(500)
		} catch (_: Throwable) {
		}
		recordingThread = null
		val pcmBytes = recordingStream?.toByteArray()
			?: return Result.failure(IllegalStateException("Recording buffer missing"))
		recordingStream = null
		audioRecord = null
		return try {
			Result.success(convertToWavBuffer(pcmBytes, sampleRate, 1, 16))
		} catch (t: Throwable) {
			Result.failure(t)
		}
	}

	private fun convertToWavBuffer(
		pcmData: ByteArray,
		sampleRate: Int,
		channels: Int,
		bitsPerSample: Int,
	): Buffer {
		val byteRate = sampleRate * channels * bitsPerSample / 8
		val blockAlign = (channels * bitsPerSample / 8).toShort()
		val totalDataLen = pcmData.size
		val chunkSize = 36 + totalDataLen
		val out = Buffer()
		val os = out.asOutputStream()
		// RIFF header
		os.write(byteArrayOf('R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte()))
		writeIntLE(os, chunkSize)
		os.write(byteArrayOf('W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte()))
		// fmt subchunk
		os.write(byteArrayOf('f'.code.toByte(), 'm'.code.toByte(), 't'.code.toByte(), ' '.code.toByte()))
		writeIntLE(os, 16) // Subchunk1Size for PCM
		writeShortLE(os, 1) // AudioFormat PCM
		writeShortLE(os, channels.toShort())
		writeIntLE(os, sampleRate)
		writeIntLE(os, byteRate)
		writeShortLE(os, blockAlign)
		writeShortLE(os, bitsPerSample.toShort())
		// data subchunk
		os.write(byteArrayOf('d'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte()))
		writeIntLE(os, totalDataLen)
		os.write(pcmData)
		os.flush()
		return out
	}

	private fun writeIntLE(os: java.io.OutputStream, value: Int) {
		os.write(
			byteArrayOf(
				(value and 0xFF).toByte(),
				((value shr 8) and 0xFF).toByte(),
				((value shr 16) and 0xFF).toByte(),
				((value shr 24) and 0xFF).toByte(),
			),
		)
	}

	private fun writeShortLE(os: java.io.OutputStream, value: Short) {
		os.write(
			byteArrayOf(
				(value.toInt() and 0xFF).toByte(),
				((value.toInt() shr 8) and 0xFF).toByte(),
			),
		)
	}

	override fun close() {
		isRecording.set(false)
		try {
			recordingThread?.join(200)
		} catch (_: Throwable) {
		}
		recordingThread = null
		try {
			audioRecord?.stop()
		} catch (_: Throwable) {
		}
		try {
			audioRecord?.release()
		} catch (_: Throwable) {
		}
		audioRecord = null
		try {
			recordingStream?.close()
		} catch (_: Throwable) {
		}
		recordingStream = null
	}
}
