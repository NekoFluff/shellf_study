package com.crazyfluff.shellfstudy.core.notifications

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationModule {
    @Binds
    abstract fun bindNotificationScheduler(impl: WorkManagerNotificationScheduler): NotificationScheduler

    @Binds
    abstract fun bindNotificationPoster(impl: SystemNotificationPoster): NotificationPoster

    @Binds
    abstract fun bindNotificationCoordinator(impl: DefaultNotificationCoordinator): NotificationCoordinator
}
