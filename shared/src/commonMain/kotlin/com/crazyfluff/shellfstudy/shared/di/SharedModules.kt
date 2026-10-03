package com.crazyfluff.shellfstudy.shared.di

import com.crazyfluff.shellfstudy.shared.ThemeViewModel
import com.crazyfluff.shellfstudy.shared.data.AccountDataCleaner
import com.crazyfluff.shellfstudy.shared.data.CmpPitchAccentBundledSource
import com.crazyfluff.shellfstudy.shared.data.AssignmentRepository
import com.crazyfluff.shellfstudy.shared.data.AssignmentStatsRepository
import com.crazyfluff.shellfstudy.shared.data.DashboardCacheRepository
import com.crazyfluff.shellfstudy.shared.data.DashboardSyncCoordinator
import com.crazyfluff.shellfstudy.shared.data.FriendRepository
import com.crazyfluff.shellfstudy.shared.data.FriendStatsRepository
import com.crazyfluff.shellfstudy.shared.data.LastSessionSummaryRepository
import com.crazyfluff.shellfstudy.shared.data.LessonSessionRepository
import com.crazyfluff.shellfstudy.shared.data.LocalHistoryGuard
import com.crazyfluff.shellfstudy.shared.data.LogoutCoordinator
import com.crazyfluff.shellfstudy.shared.data.OutboxDrainer
import com.crazyfluff.shellfstudy.shared.data.OutboxRepository
import com.crazyfluff.shellfstudy.shared.data.PitchAccentBundledSource
import com.crazyfluff.shellfstudy.shared.data.PitchAccentRepository
import com.crazyfluff.shellfstudy.shared.data.ReviewSessionRepository
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.data.StatsRepository
import com.crazyfluff.shellfstudy.shared.data.StrokeOrderRepository
import com.crazyfluff.shellfstudy.shared.data.SubjectRepository
import com.crazyfluff.shellfstudy.shared.data.TokenRepository
import com.crazyfluff.shellfstudy.shared.data.WaniKaniRepository
import com.crazyfluff.shellfstudy.shared.data.strokeorder.CmpStrokeOrderRepository
import com.crazyfluff.shellfstudy.shared.data.studytime.StudyTimeRepository
import com.crazyfluff.shellfstudy.shared.feature.auth.AuthViewModel
import com.crazyfluff.shellfstudy.shared.feature.dashboard.DashboardViewModel
import com.crazyfluff.shellfstudy.shared.feature.lastsession.LastSessionSummaryViewModel
import com.crazyfluff.shellfstudy.shared.feature.leaderboard.LeaderboardViewModel
import com.crazyfluff.shellfstudy.shared.feature.lesson.LessonViewModel
import com.crazyfluff.shellfstudy.shared.feature.review.ReviewViewModel
import com.crazyfluff.shellfstudy.shared.feature.studytime.StudyTimeViewModel
import com.crazyfluff.shellfstudy.shared.feature.search.SearchViewModel
import com.crazyfluff.shellfstudy.shared.feature.settings.SettingsViewModel
import com.crazyfluff.shellfstudy.shared.feature.splash.SplashViewModel
import com.crazyfluff.shellfstudy.shared.feature.subjectdetail.SubjectDetailViewModel
import com.crazyfluff.shellfstudy.shared.lifecycle.AppForegroundTracker
import com.crazyfluff.shellfstudy.shared.network.AuthTokenProvider
import com.crazyfluff.shellfstudy.shared.network.WaniKaniApi
import com.crazyfluff.shellfstudy.shared.network.createWaniKaniHttpClient
import com.crazyfluff.shellfstudy.shared.network.waniKaniJson
import com.crazyfluff.shellfstudy.shared.notifications.DefaultNotificationCoordinator
import com.crazyfluff.shellfstudy.shared.notifications.NotificationCoordinator
import com.crazyfluff.shellfstudy.shared.notifications.NotificationStateRepository
import com.crazyfluff.shellfstudy.shared.session.LessonSessionController
import com.crazyfluff.shellfstudy.shared.session.ReviewSessionController
import com.crazyfluff.shellfstudy.shared.sync.SyncOrchestrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.dsl.onClose

val APPLICATION_SCOPE = named("applicationScope")

val coroutineScopeModule = module {
    // onClose cancels the scope with the container. Koin tears down on stopKoin(), which the
    // Robolectric tests call between classes: without this, each test run left a live scope still
    // driving session controllers and outbox drains into the next test, and the application's own
    // onCreate guard (GlobalContext.getOrNull() == null) could bind a new graph to beans from the
    // previous one.
    single<CoroutineScope>(APPLICATION_SCOPE) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
        .onClose { it?.cancel() }
}

val appForegroundTrackerModule = module {
    single { AppForegroundTracker() }
}

val strokeOrderModule = module {
    single { CmpStrokeOrderRepository() } bind StrokeOrderRepository::class
}

val networkModule = module {
    single { waniKaniJson() }
    single { AuthTokenProvider { get<TokenRepository>().tokenFlow.firstOrNull() } }
    single { createWaniKaniHttpClient(tokenProvider = get(), json = get()) }
    single { WaniKaniApi(get()) }
}

val repositoryModule = module {
    single { WaniKaniRepository(get()) }

    single {
        SubjectRepository(
            api = get(),
            subjectDao = get(),
            srsSystemDao = get(),
            syncStateDao = get(),
            pitchAccentRepository = get()
        )
    }

    single {
        AssignmentRepository(
            api = get(),
            assignmentDao = get(),
            subjectDao = get(),
            syncStateDao = get(),
            srsSystemDao = get()
        )
    }

    single { AssignmentStatsRepository(assignmentDao = get(), subjectDao = get()) }

    single {
        StatsRepository(
            api = get(),
            reviewStatisticDao = get(),
            levelProgressionDao = get(),
            studyActivityDao = get(),
            syncStateDao = get()
        )
    }

    single { SettingsRepository(get()) }
    single { TokenRepository(get(), get()) }
    single { OutboxRepository(outboxDao = get(), outboxSyncScheduler = get(), dataStore = get()) }
    single { DashboardCacheRepository(get()) }
    single {
        StudyTimeRepository(
            studyTimeDao = get(),
            settingsRepository = get(),
            dashboardCacheRepository = get(),
            reviewStatisticDao = get(),
            assignmentDao = get(),
            applicationScope = get(APPLICATION_SCOPE)
        )
    }
    single {
        AccountDataCleaner(
            assignmentDao = get(),
            reviewStatisticDao = get(),
            levelProgressionDao = get(),
            syncStateDao = get(),
            outboxDao = get(),
            outboxRepository = get(),
            dashboardCacheRepository = get(),
            lastSessionSummaryRepository = get(),
            reviewSessionController = get(),
            lessonSessionController = get()
        )
    }
    single {
        LogoutCoordinator(
            tokenRepository = get(),
            syncScheduler = get(),
            notificationCoordinator = get(),
            accountDataCleaner = get()
        )
    }
    single { LocalHistoryGuard(dataStore = get(), studyActivityDao = get(), studyTimeDao = get()) }
    single {
        DashboardSyncCoordinator(
            waniKaniRepository = get(),
            syncOrchestrator = get(),
            dashboardCacheRepository = get(),
            localHistoryGuard = get()
        )
    }
    // Sessions persist to their own Room database rather than the shared preferences DataStore. The
    // DataStore is still passed as `legacyDataStore` so a session written before that move is drained
    // into Room on first read — see RoomSessionStore.
    single { LessonSessionRepository(sessionDao = get(), legacyDataStore = get(), json = get()) }
    single { ReviewSessionRepository(sessionDao = get(), legacyDataStore = get(), json = get()) }
    single { LastSessionSummaryRepository(dataStore = get(), json = get()) }

    // One controller per feature, shared by that feature's ViewModel and any out-of-band caller
    // (Dashboard abandon, account logout) — see QuizSessionController's doc comment for why this is
    // a process-wide singleton rather than scoped to the owning ViewModel. Registered as the
    // concrete LessonSessionController/ReviewSessionController subclasses, NOT as two
    // QuizSessionController<T> generics: Koin indexes definitions by the erased class only, so two
    // generic registrations collide on one key and the later silently overrides the earlier (the
    // lesson singleton would resolve to the review store and crash on the first lesson persist).
    single {
        LessonSessionController(
            scope = get(APPLICATION_SCOPE),
            store = get<LessonSessionRepository>()
        )
    }
    single {
        ReviewSessionController(
            scope = get(APPLICATION_SCOPE),
            store = get<ReviewSessionRepository>()
        )
    }
    single { FriendRepository(dataStore = get(), json = get(), tokenCipher = get()) }
    single {
        FriendStatsRepository(
            friendRepository = get(),
            friendStatsDao = get(),
            json = get(),
            selfAssignmentDao = get(),
            selfReviewStatisticDao = get(),
            selfLevelProgressionDao = get()
        )
    }

    single { PitchAccentRepository(bundledSource = get()) }

    // Shared rather than redeclared per platform module: this reads bundled compose resources, which
    // exist on both platforms.
    single { CmpPitchAccentBundledSource() } bind PitchAccentBundledSource::class
}

/**
 * The outbox drainer and the sync orchestrator — shared classes with shared dependencies.
 *
 * Both used to be declared once per platform with identical arguments, and the drainer was not
 * registered at all: it was constructed inline inside two different platform definitions, so its four
 * collaborators were re-listed wherever it was needed. None of that could be verified on iOS.
 */
val syncOrchestrationModule = module {
    single {
        OutboxDrainer(
            outboxDao = get(),
            waniKaniRepository = get(),
            assignmentRepository = get(),
            outboxRepository = get()
        )
    }
    single {
        SyncOrchestrator(
            transactionRunner = get(),
            subjectRepository = get(),
            assignmentRepository = get(),
            statsRepository = get(),
            syncStateDao = get()
        )
    }
}

/**
 * The platform-agnostic half of notifications: the state repository and the default coordinator.
 * Each platform supplies only a [com.crazyfluff.shellfstudy.shared.notifications.NotificationScheduler]
 * and a [com.crazyfluff.shellfstudy.shared.notifications.NotificationPoster].
 */
val notificationCoordinatorModule = module {
    single { NotificationStateRepository(get()) }
    single {
        DefaultNotificationCoordinator(
            assignmentStatsRepository = get(),
            statsRepository = get(),
            settingsRepository = get(),
            notificationStateRepository = get(),
            notificationScheduler = get(),
            notificationPoster = get()
        )
    } bind NotificationCoordinator::class
}

val viewModelModule = module {
    viewModel { ThemeViewModel(get()) }
    viewModel { StudyTimeViewModel(get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { AuthViewModel(get(), get(), get(), get(), get()) }
    viewModel { SplashViewModel(get(), get(), get()) }

    viewModel {
        SubjectDetailViewModel(
            subjectRepository = get(),
            assignmentRepository = get(),
            audioPlayer = get(),
            strokeOrderRepository = get(),
            statsRepository = get()
        )
    }

    viewModel { SearchViewModel(get()) }
    viewModel { LastSessionSummaryViewModel(get()) }
    viewModel { LeaderboardViewModel(get(), get(), get()) }

    viewModel {
        DashboardViewModel(
            reviewSessionController = get(),
            lessonSessionController = get(),
            settingsRepository = get(),
            subjectRepository = get(),
            assignmentRepository = get(),
            assignmentStatsRepository = get(),
            statsRepository = get(),
            outboxRepository = get(),
            friendStatsRepository = get(),
            logoutCoordinator = get(),
            dashboardSyncCoordinator = get(),
            lastSessionSummaryRepository = get(),
            appForegroundTracker = get(),
            studyTimeRepository = get()
        )
    }

    viewModel {
        LessonViewModel(
            assignmentRepository = get(),
            assignmentStatsRepository = get(),
            statsRepository = get(),
            outboxRepository = get(),
            sessionController = get(),
            lastSessionSummaryRepository = get(),
            pitchAccentRepository = get(),
            settingsRepository = get(),
            subjectRepository = get(),
            strokeOrderRepository = get(),
            pronunciationAudioPlayer = get(),
            appForegroundTracker = get(),
            applicationScope = get(APPLICATION_SCOPE),
            syncOrchestrator = get(),
            studyTimeRepository = get()
        )
    }

    viewModel {
        ReviewViewModel(
            assignmentRepository = get(),
            outboxRepository = get(),
            statsRepository = get(),
            sessionController = get(),
            lastSessionSummaryRepository = get(),
            pronunciationAudioPlayer = get(),
            settingsRepository = get(),
            pitchAccentRepository = get(),
            appForegroundTracker = get(),
            applicationScope = get(APPLICATION_SCOPE),
            syncOrchestrator = get(),
            studyTimeRepository = get()
        )
    }
}

/**
 * Every module both platforms use, in one list.
 *
 * Each platform's own list is `sharedAppModules + <its platform beans>`, so a module can no longer
 * exist on one platform and be missing from the other: the shared half has a single definition, and
 * what each platform adds is exactly what is platform-specific. Before this, four shared graph blocks
 * were hand-copied into both lists — SyncOrchestrator, the notification coordinator, the pitch-accent
 * source and the outbox drainer — and the iOS copy was verified by nothing at all.
 */
val sharedAppModules = listOf(
    networkModule,
    repositoryModule,
    strokeOrderModule,
    coroutineScopeModule,
    appForegroundTrackerModule,
    syncOrchestrationModule,
    notificationCoordinatorModule,
    viewModelModule
)
