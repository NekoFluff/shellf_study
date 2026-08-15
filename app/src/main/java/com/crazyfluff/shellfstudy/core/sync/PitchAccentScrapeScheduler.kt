package com.crazyfluff.shellfstudy.core.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.crazyfluff.shellfstudy.shared.sync.PitchAccentScrapeScheduler
import java.time.Duration

private const val PITCH_ACCENT_SCRAPE_WORK_NAME = "pitch_accent_scrape"

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
