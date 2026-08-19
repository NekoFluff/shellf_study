package com.crazyfluff.shellfstudy.shared.di

import com.crazyfluff.shellfstudy.shared.ThemeViewModel
import com.crazyfluff.shellfstudy.shared.data.AssignmentRepository
import com.crazyfluff.shellfstudy.shared.data.DashboardCacheRepository
import com.crazyfluff.shellfstudy.shared.data.DashboardSyncCoordinator
import com.crazyfluff.shellfstudy.shared.data.FriendRepository
import com.crazyfluff.shellfstudy.shared.data.FriendStatsRepository
import com.crazyfluff.shellfstudy.shared.data.LastSessionSummaryRepository
import com.crazyfluff.shellfstudy.shared.data.LessonSessionRepository
import com.crazyfluff.shellfstudy.shared.data.LogoutCoordinator
import com.crazyfluff.shellfstudy.shared.data.OutboxRepository
import com.crazyfluff.shellfstudy.shared.data.PitchAccentProvider
import com.crazyfluff.shellfstudy.shared.data.PitchAccentRepository
import com.crazyfluff.shellfstudy.shared.data.ReviewSessionRepository
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.data.StatsRepository
import com.crazyfluff.shellfstudy.shared.data.StrokeOrderRepository
import com.crazyfluff.shellfstudy.shared.data.SubjectRepository
import com.crazyfluff.shellfstudy.shared.data.TokenRepository
import com.crazyfluff.shellfstudy.shared.data.WaniKaniRepository
import com.crazyfluff.shellfstudy.shared.data.WeblioPitchAccentParser
import com.crazyfluff.shellfstudy.shared.data.strokeorder.CmpStrokeOrderRepository
import com.crazyfluff.shellfstudy.shared.feature.auth.AuthViewModel
import com.crazyfluff.shellfstudy.shared.feature.dashboard.DashboardViewModel
import com.crazyfluff.shellfstudy.shared.feature.lastsession.LastSessionSummaryViewModel
import com.crazyfluff.shellfstudy.shared.feature.leaderboard.LeaderboardViewModel
import com.crazyfluff.shellfstudy.shared.feature.lesson.LessonViewModel
import com.crazyfluff.shellfstudy.shared.feature.review.ReviewViewModel
import com.crazyfluff.shellfstudy.shared.feature.search.SearchViewModel
import com.crazyfluff.shellfstudy.shared.feature.settings.SettingsViewModel
import com.crazyfluff.shellfstudy.shared.feature.splash.SplashViewModel
import com.crazyfluff.shellfstudy.shared.feature.subjectdetail.SubjectDetailViewModel
import com.crazyfluff.shellfstudy.shared.lifecycle.AppForegroundTracker
import com.crazyfluff.shellfstudy.shared.network.AuthTokenProvider
import com.crazyfluff.shellfstudy.shared.network.WaniKaniApi
import com.crazyfluff.shellfstudy.shared.network.createWaniKaniHttpClient
import com.crazyfluff.shellfstudy.shared.network.waniKaniJson
import com.crazyfluff.shellfstudy.shared.network.weblio.KtorWeblioApi
import com.crazyfluff.shellfstudy.shared.network.weblio.WeblioApi
import com.crazyfluff.shellfstudy.shared.network.weblio.createWeblioHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val APPLICATION_SCOPE = named("applicationScope")

val coroutineScopeModule = module {
    single(APPLICATION_SCOPE) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
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

val weblioNetworkModule = module {
    single<WeblioApi> { KtorWeblioApi(createWeblioHttpClient()) }
}

val repositoryModule = module {
    single { WaniKaniRepository(get()) }

    single {
        SubjectRepository(
            api = get(),
            subjectDao = get(),
            srsSystemDao = get(),
            syncStateDao = get(),
            pitchAccentProvider = get()
        )
    }

    single {
        AssignmentRepository(
            api = get(),
            assignmentDao = get(),
            subjectDao = get(),
            syncStateDao = get(),
            subjectRepository = get(),
            srsSystemDao = get()
        )
    }

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
    single { WeblioPitchAccentParser() }
    single { DashboardCacheRepository(get()) }
    single { LogoutCoordinator(tokenRepository = get(), syncScheduler = get(), pitchAccentScrapeScheduler = get(), notificationCoordinator = get()) }
    single { DashboardSyncCoordinator(waniKaniRepository = get(), syncOrchestrator = get(), dashboardCacheRepository = get()) }
    single { LessonSessionRepository(dataStore = get(), json = get()) }
    single { ReviewSessionRepository(dataStore = get(), json = get()) }
    single { LastSessionSummaryRepository(dataStore = get(), json = get()) }
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

    single {
        PitchAccentRepository(
            bundledSource = get(),
            cacheDao = get(),
            weblioApi = get(),
            parser = get()
        )
    } bind PitchAccentProvider::class
}

val viewModelModule = module {
    viewModel { ThemeViewModel(get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get()) }
    viewModel { AuthViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { SplashViewModel(get(), get(), get(), get()) }

    viewModel {
        SubjectDetailViewModel(
            subjectRepository = get(),
            assignmentRepository = get(),
            settingsRepository = get(),
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
            reviewSessionRepository = get(),
            lessonSessionRepository = get(),
            settingsRepository = get(),
            subjectRepository = get(),
            assignmentRepository = get(),
            statsRepository = get(),
            outboxRepository = get(),
            outboxSyncScheduler = get(),
            friendStatsRepository = get(),
            logoutCoordinator = get(),
            dashboardSyncCoordinator = get(),
            lastSessionSummaryRepository = get(),
            appForegroundTracker = get()
        )
    }

    viewModel {
        LessonViewModel(
            assignmentRepository = get(),
            statsRepository = get(),
            outboxRepository = get(),
            lessonSessionRepository = get(),
            lastSessionSummaryRepository = get(),
            pitchAccentRepository = get(),
            settingsRepository = get(),
            subjectRepository = get(),
            strokeOrderRepository = get(),
            pronunciationAudioPlayer = get(),
            appForegroundTracker = get(),
            applicationScope = get(APPLICATION_SCOPE)
        )
    }

    viewModel {
        ReviewViewModel(
            assignmentRepository = get(),
            outboxRepository = get(),
            statsRepository = get(),
            reviewSessionRepository = get(),
            lastSessionSummaryRepository = get(),
            pronunciationAudioPlayer = get(),
            settingsRepository = get(),
            appForegroundTracker = get(),
            applicationScope = get(APPLICATION_SCOPE)
        )
    }
}
