package com.crazyfluff.shellfstudy.di

import com.crazyfluff.shellfstudy.shared.ThemeViewModel
import com.crazyfluff.shellfstudy.core.coroutines.APPLICATION_SCOPE
import com.crazyfluff.shellfstudy.feature.auth.AuthViewModel
import com.crazyfluff.shellfstudy.feature.dashboard.DashboardViewModel
import com.crazyfluff.shellfstudy.feature.lesson.LessonViewModel
import com.crazyfluff.shellfstudy.feature.review.ReviewViewModel
import com.crazyfluff.shellfstudy.feature.search.SearchViewModel
import com.crazyfluff.shellfstudy.shared.feature.settings.SettingsViewModel
import com.crazyfluff.shellfstudy.feature.splash.SplashViewModel
import com.crazyfluff.shellfstudy.feature.subjectdetail.SubjectDetailViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

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

    // A fresh instance per call site — DashboardScreen and ReviewScreen each embed their own
    // search overlay, and koinViewModel() (like hiltViewModel() before it) scopes a `viewModel { }`
    // registration to the requesting composable's own ViewModelStoreOwner rather than sharing one
    // app-wide instance.
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
