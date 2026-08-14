package com.crazyfluff.shellfstudy.di

import com.crazyfluff.shellfstudy.core.notifications.DailyStreakReminderWorker
import com.crazyfluff.shellfstudy.core.notifications.DeferredNotificationWorker
import com.crazyfluff.shellfstudy.core.notifications.ReviewNotificationWorker
import com.crazyfluff.shellfstudy.core.sync.OutboxSyncWorker
import com.crazyfluff.shellfstudy.core.sync.PitchAccentScrapeWorker
import com.crazyfluff.shellfstudy.core.sync.SyncWorker
import org.koin.androidx.workmanager.dsl.worker
import org.koin.dsl.module

val workerModule = module {
    worker {
        OutboxSyncWorker(
            appContext = it.get(),
            params = it.get(),
            outboxDao = get(),
            waniKaniRepository = get(),
            assignmentRepository = get(),
            outboxRepository = get()
        )
    }

    worker {
        PitchAccentScrapeWorker(
            appContext = it.get(),
            params = it.get(),
            subjectDao = get(),
            pitchAccentCacheDao = get(),
            pitchAccentRepository = get()
        )
    }

    worker {
        SyncWorker(
            appContext = it.get(),
            params = it.get(),
            syncOrchestrator = get(),
            notificationCoordinator = get(),
            outboxSyncScheduler = get()
        )
    }

    worker {
        DailyStreakReminderWorker(
            appContext = it.get(),
            params = it.get(),
            notificationCoordinator = get()
        )
    }

    worker {
        DeferredNotificationWorker(
            appContext = it.get(),
            params = it.get(),
            notificationCoordinator = get()
        )
    }

    worker {
        ReviewNotificationWorker(
            appContext = it.get(),
            params = it.get(),
            notificationCoordinator = get()
        )
    }
}
