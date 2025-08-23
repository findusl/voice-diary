package de.lehrbaum.voiry

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main(): Unit =
	application {
		Window(
			onCloseRequest = ::exitApplication,
			title = "Voice Diary",
		) {
			App()
		}
	}
