package com.crazyfluff.shellfstudy.core.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.workDataOf
import com.crazyfluff.shellfstudy.fakes.buildTestWorker
import com.crazyfluff.shellfstudy.fakes.FakeNotificationCoordinator
import com.crazyfluff.shellfstudy.shared.notifications.DeferredNotificationCategory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class DeferredNotificationWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun buildWorker(
        coordinator: FakeNotificationCoordinator,
        category: String? = null
    ): DeferredNotificationWorker {
        return buildTestWorker(
            context = context,
            inputData = category?.let { workDataOf(DeferredNotificationWorker.KEY_CATEGORY to it) }
        ) { appContext, params ->
            DeferredNotificationWorker(appContext, params, coordinator)
        }
    }

    @Test
    fun `BACKLOG category triggers evaluateReviewsAndBacklog`() = runTest {
        val coordinator = FakeNotificationCoordinator()

        val result = buildWorker(coordinator, DeferredNotificationCategory.BACKLOG).doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        assertThat(coordinator.evaluateReviewsAndBacklogCallCount).isEqualTo(1)
    }

    @Test
    fun `STUDY_REMINDER category triggers evaluateStudyReminder`() = runTest {
        val coordinator = FakeNotificationCoordinator()

        val result = buildWorker(coordinator, DeferredNotificationCategory.STUDY_REMINDER).doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        assertThat(coordinator.evaluateStudyReminderCallCount).isEqualTo(1)
    }

    @Test
    fun `retries instead of crashing when the evaluation throws`() = runTest {
        val coordinator = FakeNotificationCoordinator().apply {
            throwOnEvaluateReviewsAndBacklog = IOException("offline")
        }

        val result = buildWorker(coordinator, DeferredNotificationCategory.BACKLOG).doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.retry())
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
