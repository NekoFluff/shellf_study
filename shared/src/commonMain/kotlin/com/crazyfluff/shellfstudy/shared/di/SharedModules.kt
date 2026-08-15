package com.crazyfluff.shellfstudy.shared.di

import com.crazyfluff.shellfstudy.shared.ThemeViewModel
import com.crazyfluff.shellfstudy.shared.data.AssignmentRepository
import com.crazyfluff.shellfstudy.shared.data.DashboardCacheRepository
import com.crazyfluff.shellfstudy.shared.data.LessonSessionRepository
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
    single<com.crazyfluff.shellfstudy.shared.network.weblio.WeblioApi> {
        com.crazyfluff.shellfstudy.shared.network.weblio.KtorWeblioApi(
            com.crazyfluff.shellfstudy.shared.network.weblio.createWeblioHttpClient()
        )
    }
}

val repositoryModule = module {
    single { WaniKaniRepository(get()) }

    single {
        SubjectRepository(
            api = get(),
            subjectDao = get(),
            srsSystemDao = get(),
            studyMaterialDao = get(),
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
    single { LessonSessionRepository(dataStore = get(), json = get()) }
    single { ReviewSessionRepository(dataStore = get(), json = get()) }

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
    viewModel { AuthViewModel(get(), get(), get(), get(), get()) }
    viewModel { SplashViewModel(get(), get(), get(), get()) }

    viewModel {
        SubjectDetailViewModel(
            subjectRepository = get(),
            assignmentRepository = get(),
            settingsRepository = get(),
            audioPlayer = get(),
            strokeOrderRepository = get()
        )
    }

    viewModel { SearchViewModel(get()) }

    viewModel {
        DashboardViewModel(
            waniKaniRepository = get(),
            tokenRepository = get(),
            reviewSessionRepository = get(),
            lessonSessionRepository = get(),
            settingsRepository = get(),
            subjectRepository = get(),
            assignmentRepository = get(),
            statsRepository = get(),
            dashboardCacheRepository = get(),
            outboxRepository = get(),
            syncOrchestrator = get(),
            syncScheduler = get(),
            pitchAccentScrapeScheduler = get(),
            notificationCoordinator = get()
        )
    }

    viewModel {
        LessonViewModel(
            assignmentRepository = get(),
            outboxRepository = get(),
            lessonSessionRepository = get(),
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
            pronunciationAudioPlayer = get(),
            settingsRepository = get(),
            appForegroundTracker = get(),
            applicationScope = get(APPLICATION_SCOPE)
        )
    }
}
