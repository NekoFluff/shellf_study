package com.crazyfluff.shellfstudy.core.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration

private const val PITCH_ACCENT_SCRAPE_WORK_NAME = "pitch_accent_scrape"

/**
 * Schedules the daily background pitch-accent scrape — enqueued/cancelled alongside
 * [SyncScheduler] (same login/logout lifecycle), so vocab pitch data keeps filling in over time
 * without a per-view trigger. An interface for the same testability reason as [SyncScheduler].
 */
interface PitchAccentScrapeScheduler {
    fun schedulePeriodicScrape()
    fun cancelPeriodicScrape()
}

class WorkManagerPitchAccentScrapeScheduler(
    private val context: Context
) : PitchAccentScrapeScheduler {
    override fun schedulePeriodicScrape() {
        val request = PeriodicWorkRequestBuilder<PitchAccentScrapeWorker>(
            repeatInterval = Duration.ofDays(1),
            flexTimeInterval = Duration.ofHours(1)
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PITCH_ACCENT_SCRAPE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun cancelPeriodicScrape() {
        WorkManager.getInstance(context).cancelUniqueWork(PITCH_ACCENT_SCRAPE_WORK_NAME)
    }
}
