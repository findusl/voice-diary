package de.lehrbaum.voiry

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityStartTest {
	@Test
	fun appLaunches() {
		ActivityScenario.launch(MainActivity::class.java).use { }
	}
}
