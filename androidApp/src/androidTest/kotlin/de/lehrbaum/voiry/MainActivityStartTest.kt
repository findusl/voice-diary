package de.lehrbaum.voiry

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityStartTest {
	private val composeTestRule = createAndroidComposeRule<MainActivity>()

	@get:Rule
	val rules: RuleChain = RuleChain
		.outerRule(localNetworkPermissionRule())
		.around(composeTestRule)

	@Test
	fun appLaunches() {
		composeTestRule.onNodeWithText("Voice Diary").assertIsDisplayed()
	}

	private fun localNetworkPermissionRule(): TestRule =
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
			GrantPermissionRule.grant(Manifest.permission.ACCESS_LOCAL_NETWORK)
		} else {
			TestRule { statement, _ -> statement }
		}
}
