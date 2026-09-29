package com.crazyfluff.shellfstudy.core.notifications

import androidx.work.ListenableWorker.Result
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs [block], turning a failure into [Result.retry] — except cancellation, which is rethrown.
 *
 * `runCatching` would catch the [CancellationException] WorkManager uses to stop a worker, and
 * answering it with a retry keeps a cancelled chain alive.
 */
// Any failure is worth a retry here, and the exception has nowhere better to go: WorkManager only
// hears success or retry.
@Suppress("TooGenericExceptionCaught", "SwallowedException")
internal inline fun retryOnFailure(block: () -> Unit): Result = try {
    block()
    Result.success()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.retry()
}
