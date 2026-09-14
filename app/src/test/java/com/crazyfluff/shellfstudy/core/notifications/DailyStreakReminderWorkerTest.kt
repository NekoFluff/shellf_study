package com.crazyfluff.shellfstudy.core.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.crazyfluff.shellfstudy.fakes.FakeNotificationCoordinator
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DailyStreakReminderWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun buildWorker(coordinator: FakeNotificationCoordinator): DailyStreakReminderWorker =
        TestListenableWorkerBuilder<DailyStreakReminderWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker = DailyStreakReminderWorker(appContext, workerParameters, coordinator)
            })
            .build()

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
