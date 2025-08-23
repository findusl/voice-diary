package de.lehrbaum.voiry

import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel
import io.github.aakira.napier.Napier
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

actual fun initLogging() {
	Napier.base(CustomAntilog())
}

private class CustomAntilog(
	private val defaultTag: String = "App",
	private val minLogLevel: LogLevel = LogLevel.DEBUG,
) : Antilog() {
	override fun performLog(
		priority: LogLevel,
		tag: String?,
		throwable: Throwable?,
		message: String?,
	) {
		if (priority.ordinal < minLogLevel.ordinal) return
		val formattedTag = tag ?: defaultTag
		val formattedMessage = buildLogMessage(priority, formattedTag, throwable, message)

		@Suppress("ReplacePrintlnWithLogging") // here it is ok for now
		when (priority) {
			LogLevel.VERBOSE, LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARNING -> println(formattedMessage)
			LogLevel.ERROR, LogLevel.ASSERT -> System.err.println(formattedMessage)
		}
	}

	private fun buildLogMessage(
		priority: LogLevel,
		tag: String,
		throwable: Throwable?,
		message: String?,
	): String {
		val logLevelChar = priority.name.first()
		val timestamp = ZonedDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
		val baseMessage = "$logLevelChar $timestamp $tag: $message"

		return baseMessage + (throwable?.let { "\n${it.stackTraceToString()}" } ?: "")
	}
}
