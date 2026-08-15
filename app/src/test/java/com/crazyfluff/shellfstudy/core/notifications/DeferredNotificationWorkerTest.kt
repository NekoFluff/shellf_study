package com.crazyfluff.shellfstudy.core.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.crazyfluff.shellfstudy.fakes.FakeNotificationCoordinator
import com.crazyfluff.shellfstudy.shared.notifications.DeferredNotificationCategory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DeferredNotificationWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun buildWorker(
        coordinator: FakeNotificationCoordinator,
        category: String? = null
    ): DeferredNotificationWorker {
        val builder = TestListenableWorkerBuilder<DeferredNotificationWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker = DeferredNotificationWorker(appContext, workerParameters, coordinator)
            })
        if (category != null) {
            builder.setInputData(workDataOf(DeferredNotificationWorker.KEY_CATEGORY to category))
        }
        return builder.build()
    }

    @Test
    fun `BACKLOG category triggers evaluateReviewsAndBacklog`() = runTest {
        val coordinator = FakeNotificationCoordinator()

        val result = buildWorker(coordinator, DeferredNotificationCategory.BACKLOG).doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        assertThat(coordinator.evaluateReviewsAndBacklogCallCount).isEqualTo(1)
    }

    @Test
    fun `unknown category is a no-op but still returns success`() = runTest {
        val coordinator = FakeNotificationCoordinator()

        val result = buildWorker(coordinator, "some_unknown_category").doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        assertThat(coordinator.evaluateReviewsAndBacklogCallCount).isEqualTo(0)
    }

    @Test
    fun `absent category is a no-op but still returns success`() = runTest {
        val coordinator = FakeNotificationCoordinator()

        val result = buildWorker(coordinator, category = null).doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        assertThat(coordinator.evaluateReviewsAndBacklogCallCount).isEqualTo(0)
    }
}
