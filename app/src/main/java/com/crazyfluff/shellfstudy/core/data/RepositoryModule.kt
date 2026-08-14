package com.crazyfluff.shellfstudy.core.data

import com.crazyfluff.shellfstudy.shared.data.AssignmentRepository
import com.crazyfluff.shellfstudy.shared.data.DashboardCacheRepository
import com.crazyfluff.shellfstudy.shared.data.LessonSessionRepository
import com.crazyfluff.shellfstudy.shared.data.OutboxRepository
import com.crazyfluff.shellfstudy.shared.data.PitchAccentProvider
import com.crazyfluff.shellfstudy.shared.data.ReviewSessionRepository
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.data.StatsRepository
import com.crazyfluff.shellfstudy.shared.data.SubjectRepository
import com.crazyfluff.shellfstudy.shared.data.TokenRepository
import com.crazyfluff.shellfstudy.shared.data.WaniKaniRepository
import com.crazyfluff.shellfstudy.shared.data.WeblioPitchAccentParser
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Explicit registrations for repositories that live in :shared as plain classes (Koin has no
 * constructor scanning, so every injectable class — whether it lives here or in :shared — needs
 * an explicit `single { }`/`viewModel { }`/`worker { }` entry somewhere in this app's Koin graph).
 */
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

    // Implements PitchAccentProvider (the small interface SubjectRepository actually needs) since
    // this class itself isn't portable yet — see PitchAccentRepository's own doc comment.
    single {
        PitchAccentRepository(
            bundledSource = get(),
            cacheDao = get(),
            weblioApi = get(),
            parser = get()
        )
    } bind PitchAccentProvider::class
}
