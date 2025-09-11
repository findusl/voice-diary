package de.lehrbaum.voiry.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
	private val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : MainDispatcherTest {
	override fun setup() {
		Dispatchers.setMain(dispatcher)
	}

	override fun tearDown() {
		Dispatchers.resetMain()
	}
}
