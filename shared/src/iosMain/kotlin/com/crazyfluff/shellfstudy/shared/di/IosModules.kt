package com.crazyfluff.shellfstudy.shared.di

import com.crazyfluff.shellfstudy.shared.data.CmpPitchAccentBundledSource
import com.crazyfluff.shellfstudy.shared.data.IosPronunciationAudioPlayer
import com.crazyfluff.shellfstudy.shared.data.OutboxSyncScheduler
import com.crazyfluff.shellfstudy.shared.data.PitchAccentBundledSource
import com.crazyfluff.shellfstudy.shared.data.PronunciationAudioPlayer
import com.crazyfluff.shellfstudy.shared.data.TokenCipher
import com.crazyfluff.shellfstudy.shared.data.KeychainTokenCipher
import com.crazyfluff.shellfstudy.shared.data.getPreferencesDataStore
import com.crazyfluff.shellfstudy.shared.database.AppDatabase
import com.crazyfluff.shellfstudy.shared.database.buildAppDatabase
import com.crazyfluff.shellfstudy.shared.database.getAppDatabaseBuilder
import com.crazyfluff.shellfstudy.shared.database.outbox.OutboxDatabase
import com.crazyfluff.shellfstudy.shared.database.outbox.buildOutboxDatabase
import com.crazyfluff.shellfstudy.shared.database.outbox.getOutboxDatabaseBuilder
import com.crazyfluff.shellfstudy.shared.database.pitchaccent.PitchAccentDatabase
import com.crazyfluff.shellfstudy.shared.database.pitchaccent.buildPitchAccentDatabase
import com.crazyfluff.shellfstudy.shared.database.pitchaccent.getPitchAccentDatabaseBuilder
import com.crazyfluff.shellfstudy.shared.database.studyactivity.StudyActivityDatabase
import com.crazyfluff.shellfstudy.shared.database.studyactivity.buildStudyActivityDatabase
import com.crazyfluff.shellfstudy.shared.database.studyactivity.getStudyActivityDatabaseBuilder
import com.crazyfluff.shellfstudy.shared.notifications.DefaultNotificationCoordinator
import com.crazyfluff.shellfstudy.shared.notifications.NotificationCoordinator
import com.crazyfluff.shellfstudy.shared.notifications.NotificationPoster
import com.crazyfluff.shellfstudy.shared.notifications.NotificationScheduler
import com.crazyfluff.shellfstudy.shared.notifications.NotificationStateRepository
import com.crazyfluff.shellfstudy.shared.sync.PitchAccentScrapeScheduler
import com.crazyfluff.shellfstudy.shared.sync.SyncOrchestrator
import com.crazyfluff.shellfstudy.shared.sync.SyncScheduler
import kotlin.time.Instant
import org.koin.dsl.bind
import org.koin.dsl.module

private val iosDatabaseModule = module {
    single { buildAppDatabase(getAppDatabaseBuilder()) }
    single { get<AppDatabase>().subjectDao() }
    single { get<AppDatabase>().assignmentDao() }
    single { get<AppDatabase>().srsSystemDao() }
    single { get<AppDatabase>().reviewStatisticDao() }
    single { get<AppDatabase>().studyMaterialDao() }
    single { get<AppDatabase>().levelProgressionDao() }
    single { get<AppDatabase>().syncStateDao() }

    single { buildStudyActivityDatabase(getStudyActivityDatabaseBuilder()) }
    single { get<StudyActivityDatabase>().studyActivityDao() }

    single { buildOutboxDatabase(getOutboxDatabaseBuilder()) }
    single { get<OutboxDatabase>().outboxDao() }

    single { buildPitchAccentDatabase(getPitchAccentDatabaseBuilder()) }
    single { get<PitchAccentDatabase>().pitchAccentCacheDao() }
}

private val iosDataStoreModule = module {
    single { getPreferencesDataStore() }
    single<TokenCipher> { KeychainTokenCipher() }
    single { CmpPitchAccentBundledSource() } bind PitchAccentBundledSource::class
}

private val iosAudioModule = module {
    single { IosPronunciationAudioPlayer() } bind PronunciationAudioPlayer::class
}

/** Background sync is handled via BGTaskScheduler on iOS — stubbed as no-ops here until that
 *  scheduler is wired up. Reviews and subjects still sync whenever the app foregrounds via the
 *  existing SyncOrchestrator calls from the Dashboard. */
private val iosSyncModule = module {
    single<SyncScheduler> { object : SyncScheduler {
        override fun schedulePeriodicSync() = Unit
        override fun cancelPeriodicSync() = Unit
    }}
    single<PitchAccentScrapeScheduler> { object : PitchAccentScrapeScheduler {
        override fun schedulePeriodicScrape() = Unit
        override fun cancelPeriodicScrape() = Unit
    }}
    single<OutboxSyncScheduler> { OutboxSyncScheduler { } }
    single {
        SyncOrchestrator(
            subjectRepository = get(),
            assignmentRepository = get(),
            statsRepository = get(),
            syncStateDao = get()
        )
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

val iosAppModules = listOf(
    iosDatabaseModule,
    iosDataStoreModule,
    iosAudioModule,
    networkModule,
    weblioNetworkModule,
    repositoryModule,
    strokeOrderModule,
    coroutineScopeModule,
    appForegroundTrackerModule,
    iosSyncModule,
    iosNotificationModule,
    viewModelModule,
)
