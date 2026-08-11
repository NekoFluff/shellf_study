package com.crazyfluff.shellfstudy.core.sync

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds
    abstract fun bindSyncScheduler(impl: WorkManagerSyncScheduler): SyncScheduler

    @Binds
    abstract fun bindPitchAccentScrapeScheduler(
        impl: WorkManagerPitchAccentScrapeScheduler
    ): PitchAccentScrapeScheduler
}
