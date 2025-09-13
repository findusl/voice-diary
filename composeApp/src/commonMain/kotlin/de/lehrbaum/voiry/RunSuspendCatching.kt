package de.lehrbaum.voiry

import kotlinx.coroutines.CancellationException

inline fun <T> runSuspendCatching(block: () -> T): Result<T> =
	try {
		Result.success(block())
	} catch (e: CancellationException) {
		throw e
	} catch (e: Throwable) {
		Result.failure(e)
	}
