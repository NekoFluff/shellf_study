package com.crazyfluff.shellfstudy.core.notifications

import com.crazyfluff.shellfstudy.shared.notifications.NotificationPoster
import com.crazyfluff.shellfstudy.shared.notifications.NotificationScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module

val notificationModule = module {
    single { WorkManagerNotificationScheduler(androidContext()) } bind NotificationScheduler::class
    single { SystemNotificationPoster(androidContext()) } bind NotificationPoster::class
}
