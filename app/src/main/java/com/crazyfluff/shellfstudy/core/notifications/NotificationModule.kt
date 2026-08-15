package com.crazyfluff.shellfstudy.core.notifications

import com.crazyfluff.shellfstudy.shared.notifications.DefaultNotificationCoordinator
import com.crazyfluff.shellfstudy.shared.notifications.NotificationCoordinator
import com.crazyfluff.shellfstudy.shared.notifications.NotificationPoster
import com.crazyfluff.shellfstudy.shared.notifications.NotificationScheduler
import com.crazyfluff.shellfstudy.shared.notifications.NotificationStateRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module

val notificationModule = module {
    single { WorkManagerNotificationScheduler(androidContext()) } bind NotificationScheduler::class
    single { SystemNotificationPoster(androidContext()) } bind NotificationPoster::class
    single { NotificationStateRepository(get()) }
    single {
        DefaultNotificationCoordinator(
            assignmentRepository = get(),
            statsRepository = get(),
            settingsRepository = get(),
            notificationStateRepository = get(),
            notificationScheduler = get(),
            notificationPoster = get()
        )
    } bind NotificationCoordinator::class
}
