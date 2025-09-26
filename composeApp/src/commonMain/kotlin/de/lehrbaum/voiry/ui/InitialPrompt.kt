package de.lehrbaum.voiry.ui

internal fun initialPromptFromTitle(title: String?): String? {
	val trimmed = title?.trim().orEmpty()
	if (trimmed.isEmpty()) return null
	val last = trimmed.last()
	return if (last.isLetterOrDigit()) {
		"$trimmed."
	} else {
		trimmed
	}
}
