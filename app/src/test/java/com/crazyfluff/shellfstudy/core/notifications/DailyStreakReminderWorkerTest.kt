package com.crazyfluff.shellfstudy.core.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import com.crazyfluff.shellfstudy.fakes.buildTestWorker
import com.crazyfluff.shellfstudy.fakes.FakeNotificationCoordinator
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class DailyStreakReminderWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun buildWorker(coordinator: FakeNotificationCoordinator): DailyStreakReminderWorker =
        buildTestWorker(context) { appContext, params ->
            DailyStreakReminderWorker(appContext, params, coordinator)
        }

    @Test
    fun `evaluates and reschedules tomorrow's reminder on success`() = runTest {
        val coordinator = FakeNotificationCoordinator()

        val result = buildWorker(coordinator).doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        assertThat(coordinator.evaluateStudyReminderCallCount).isEqualTo(1)
        assertThat(coordinator.rescheduleDailyReminderCallCount).isEqualTo(1)
    }

    @Test
    fun `retries instead of crashing when evaluation throws`() = runTest {
        val coordinator = FakeNotificationCoordinator().apply {
            throwOnEvaluateStudyReminder = IOException("offline")
        }

        val result = buildWorker(coordinator).doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.retry())
    }

    @Test
    fun `retries instead of crashing when rescheduling throws`() = runTest {
        val coordinator = FakeNotificationCoordinator().apply {
            throwOnRescheduleDailyReminder = IOException("db unavailable")
        }

        val result = buildWorker(coordinator).doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.retry())
    }
}
