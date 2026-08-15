package com.crazyfluff.shellfstudy.di

import com.crazyfluff.shellfstudy.core.notifications.DailyStreakReminderWorker
import com.crazyfluff.shellfstudy.core.notifications.DeferredNotificationWorker
import com.crazyfluff.shellfstudy.core.notifications.ReviewNotificationWorker
import com.crazyfluff.shellfstudy.core.sync.OutboxSyncWorker
import com.crazyfluff.shellfstudy.core.sync.PitchAccentScrapeWorker
import com.crazyfluff.shellfstudy.core.sync.SyncWorker
import com.crazyfluff.shellfstudy.shared.data.OutboxDrainer
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.worker
import org.koin.dsl.module

// In Koin 4.x, KoinWorkerFactory only passes WorkerParameters to the factory's `it` — appContext
// is no longer included. Use androidContext() (Koin's bound Application context) for appContext,
// and it.get() for the WorkerParameters.
val workerModule = module {
    worker {
        OutboxSyncWorker(
            appContext = androidContext(),
            params = it.get(),
            outboxDrainer = OutboxDrainer(
                outboxDao = get(),
                waniKaniRepository = get(),
                assignmentRepository = get(),
                outboxRepository = get()
            )
        )
    }

    worker {
        PitchAccentScrapeWorker(
            appContext = androidContext(),
            params = it.get(),
            subjectDao = get(),
            pitchAccentCacheDao = get(),
            pitchAccentRepository = get()
        )
    }

    worker {
        SyncWorker(
            appContext = androidContext(),
            params = it.get(),
            syncOrchestrator = get(),
            notificationCoordinator = get(),
            outboxSyncScheduler = get()
        )
    }

    worker {
        DailyStreakReminderWorker(
            appContext = androidContext(),
            params = it.get(),
            notificationCoordinator = get()
        )
    }

    worker {
        DeferredNotificationWorker(
            appContext = androidContext(),
            params = it.get(),
            notificationCoordinator = get()
        )
    }

    worker {
        ReviewNotificationWorker(
            appContext = androidContext(),
            params = it.get(),
            notificationCoordinator = get()
        )
    }
}
