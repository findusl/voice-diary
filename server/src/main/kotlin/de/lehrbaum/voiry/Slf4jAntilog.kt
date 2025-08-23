package de.lehrbaum.voiry

import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel
import org.slf4j.LoggerFactory

class Slf4jAntilog : Antilog() {
	override fun performLog(
		priority: LogLevel,
		tag: String?,
		throwable: Throwable?,
		message: String?,
	) {
		val logger = LoggerFactory.getLogger(tag ?: "Napier")
		val msg = message ?: ""
		when (priority) {
			LogLevel.VERBOSE -> logger.trace(msg, throwable)
			LogLevel.DEBUG -> logger.debug(msg, throwable)
			LogLevel.INFO -> logger.info(msg, throwable)
			LogLevel.WARNING -> logger.warn(msg, throwable)
			LogLevel.ERROR, LogLevel.ASSERT -> logger.error(msg, throwable)
		}
	}
}
