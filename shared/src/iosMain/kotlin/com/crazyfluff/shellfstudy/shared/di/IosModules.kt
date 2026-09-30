package com.crazyfluff.shellfstudy.shared.di

import com.crazyfluff.shellfstudy.shared.data.IosOutboxSyncScheduler
import com.crazyfluff.shellfstudy.shared.data.IosPronunciationAudioPlayer
import com.crazyfluff.shellfstudy.shared.data.audio.IosAudioFileCache
import com.crazyfluff.shellfstudy.shared.data.OutboxSyncScheduler
import com.crazyfluff.shellfstudy.shared.data.PronunciationAudioPlayer
import com.crazyfluff.shellfstudy.shared.data.TokenCipher
import com.crazyfluff.shellfstudy.shared.data.KeychainTokenCipher
import com.crazyfluff.shellfstudy.shared.data.getPreferencesDataStore
import com.crazyfluff.shellfstudy.shared.database.getAppDatabaseBuilder
import com.crazyfluff.shellfstudy.shared.database.friends.getFriendsDatabaseBuilder
import com.crazyfluff.shellfstudy.shared.database.outbox.getOutboxDatabaseBuilder
import com.crazyfluff.shellfstudy.shared.database.studyactivity.getStudyActivityDatabaseBuilder
import com.crazyfluff.shellfstudy.shared.database.studytime.getStudyTimeDatabaseBuilder
import com.crazyfluff.shellfstudy.shared.database.session.getSessionDatabaseBuilder
import com.crazyfluff.shellfstudy.shared.di.registerDatabases
import com.crazyfluff.shellfstudy.shared.notifications.NotificationPoster
import com.crazyfluff.shellfstudy.shared.notifications.NotificationScheduler
import com.crazyfluff.shellfstudy.shared.sync.SyncScheduler
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.dsl.bind
import org.koin.dsl.module

private val iosDatabaseModule = module {
    registerDatabases(
        appDatabaseBuilder = { getAppDatabaseBuilder() },
        studyActivityDatabaseBuilder = { getStudyActivityDatabaseBuilder() },
        outboxDatabaseBuilder = { getOutboxDatabaseBuilder() },
        friendsDatabaseBuilder = { getFriendsDatabaseBuilder() },
        sessionDatabaseBuilder = { getSessionDatabaseBuilder() },
        studyTimeDatabaseBuilder = { getStudyTimeDatabaseBuilder() }
    )
}

private val iosDataStoreModule = module {
    single { getPreferencesDataStore() }
    single<TokenCipher> { KeychainTokenCipher() }
}

private val iosAudioModule = module {
    single { IosAudioFileCache() }
    single { IosPronunciationAudioPlayer(get()) } bind PronunciationAudioPlayer::class
}

/** Periodic background sync (BGTaskScheduler) is stubbed — it fires opportunistically from the
 *  Dashboard instead. The outbox scheduler, however, needs to actually drain, so it launches the
 *  drainer in the app-level coroutine scope whenever called. Network errors are handled inside
 *  OutboxDrainer; the launch is fire-and-forget (duplicates are harmless, the drainer is
 *  idempotent). */
private val iosSyncModule = module {
    single<SyncScheduler> { object : SyncScheduler {
        override fun schedulePeriodicSync() = Unit
        override fun cancelPeriodicSync() = Unit
    }}
    single<OutboxSyncScheduler> {
        IosOutboxSyncScheduler(get<CoroutineScope>(APPLICATION_SCOPE), get())
    }
}

/** Push notifications on iOS require UNUserNotificationCenter integration — stubbed as no-ops
 *  here so the DashboardViewModel (which calls notificationCoordinator.onLogin) compiles and runs
 *  without crashing. Real iOS push support is a follow-up feature. */
private val iosNotificationModule = module {
    single<NotificationScheduler> { object : NotificationScheduler {
        override fun scheduleNextReviewCheck(targetInstant: Instant?) = Unit
        override fun scheduleDailyStreakReminder(hour: Int, minute: Int) = Unit
        override fun cancelNextReviewCheck() = Unit
        override fun cancelDailyStreakReminder() = Unit
        override fun cancelAll() = Unit
        override fun scheduleDeferredNotification(category: String, targetInstant: Instant) = Unit
    }}
    single<NotificationPoster> { object : NotificationPoster {
        override fun canPost(): Boolean = false
        override fun post(spec: com.crazyfluff.shellfstudy.shared.notifications.NotificationSpec) = Unit
        override fun cancel(id: Int) = Unit
    }}
}

/**
 * The shared graph plus this platform's beans — the outbox drainer, sync orchestrator, notification
 * coordinator and pitch-accent source all come from [sharedAppModules] now.
 *
 * List order matters: Room's `single` factories and the ViewModels resolve lazily, but keeping the
 * shared modules first means a platform module overriding one of them is the visible exception rather
 * than the rule.
 */
val iosAppModules = sharedAppModules + listOf(
    iosDatabaseModule,
    iosDataStoreModule,
    iosAudioModule,
    iosSyncModule,
    iosNotificationModule,
)
