package de.lehrbaum.voiry

import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier

actual fun initLogging() {
	Napier.base(DebugAntilog())
}
