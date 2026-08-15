package com.crazyfluff.shellfstudy.core.sync

import com.crazyfluff.shellfstudy.shared.data.OutboxSyncScheduler
import com.crazyfluff.shellfstudy.shared.sync.PitchAccentScrapeScheduler
import com.crazyfluff.shellfstudy.shared.sync.SyncOrchestrator
import com.crazyfluff.shellfstudy.shared.sync.SyncScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module

val syncModule = module {
    single { WorkManagerSyncScheduler(androidContext()) } bind SyncScheduler::class
    single { WorkManagerPitchAccentScrapeScheduler(androidContext()) } bind PitchAccentScrapeScheduler::class
    single { WorkManagerOutboxSyncScheduler(androidContext()) } bind OutboxSyncScheduler::class
    single {
        SyncOrchestrator(
            subjectRepository = get(),
            assignmentRepository = get(),
            statsRepository = get(),
            syncStateDao = get()
        )
    }
}
