package com.crazyfluff.shellfstudy.core.notifications

import androidx.work.ListenableWorker.Result
import com.google.common.truth.Truth.assertThat
import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkerRetryTest {

    @Test
    fun `a block that completes succeeds`() {
        assertThat(retryOnFailure { }).isEqualTo(Result.success())
    }

    @Test
    fun `a block that throws is retried`() {
        assertThat(retryOnFailure { error("offline") }).isEqualTo(Result.retry())
    }

    @Test
    fun `cancellation is rethrown rather than answered with a retry`() {
        assertThrows(CancellationException::class.java) {
            retryOnFailure { throw CancellationException("worker stopped") }
        }
    }
}
