package de.lehrbaum.voiry.ui

import kotlin.test.AfterTest
import kotlin.test.BeforeTest

interface MainDispatcherTest {
	@BeforeTest
	fun setup()

	@AfterTest
	fun tearDown()
}
