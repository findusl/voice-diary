package de.lehrbaum.voiry

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class MainActivityTest {
	@get:Rule
	val composeTestRule = createAndroidComposeRule<MainActivity>()

	@Test
	fun appBarTitleDisplayed() {
		composeTestRule.onNodeWithText("Voice Diary").assertIsDisplayed()
	}
}
