package com.crazyfluff.shellfstudy.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.crazyfluff.shellfstudy.shared.data.AssignmentRepository
import com.crazyfluff.shellfstudy.shared.data.OutboxRepository
import com.crazyfluff.shellfstudy.shared.data.OutboxSyncScheduler
import com.crazyfluff.shellfstudy.shared.data.PitchAccentProvider
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.data.StatsRepository
import com.crazyfluff.shellfstudy.shared.data.SubjectRepository
import com.crazyfluff.shellfstudy.shared.data.TokenCipher
import com.crazyfluff.shellfstudy.shared.data.TokenRepository
import com.crazyfluff.shellfstudy.shared.data.WaniKaniRepository
import com.crazyfluff.shellfstudy.shared.database.AssignmentDao
import com.crazyfluff.shellfstudy.shared.database.LevelProgressionDao
import com.crazyfluff.shellfstudy.shared.database.ReviewStatisticDao
import com.crazyfluff.shellfstudy.shared.database.SrsSystemDao
import com.crazyfluff.shellfstudy.shared.database.StudyMaterialDao
import com.crazyfluff.shellfstudy.shared.database.SubjectDao
import com.crazyfluff.shellfstudy.shared.database.SyncStateDao
import com.crazyfluff.shellfstudy.shared.database.outbox.OutboxDao
import com.crazyfluff.shellfstudy.shared.database.studyactivity.StudyActivityDao
import com.crazyfluff.shellfstudy.shared.network.WaniKaniApi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Explicit @Provides bindings for repositories that live in :shared as plain classes (no
 * javax.inject annotations there — that library doesn't support Kotlin/Native, so DI wiring for
 * anything in commonMain has to happen here instead of via @Inject constructor).
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideWaniKaniRepository(api: WaniKaniApi): WaniKaniRepository = WaniKaniRepository(api)

    @Provides
    @Singleton
    fun provideSubjectRepository(
        api: WaniKaniApi,
        subjectDao: SubjectDao,
        srsSystemDao: SrsSystemDao,
        studyMaterialDao: StudyMaterialDao,
        syncStateDao: SyncStateDao,
        pitchAccentProvider: PitchAccentProvider
    ): SubjectRepository = SubjectRepository(api, subjectDao, srsSystemDao, studyMaterialDao, syncStateDao, pitchAccentProvider)

    @Provides
    @Singleton
    fun provideAssignmentRepository(
        api: WaniKaniApi,
        assignmentDao: AssignmentDao,
        subjectDao: SubjectDao,
        syncStateDao: SyncStateDao,
        subjectRepository: SubjectRepository,
        srsSystemDao: SrsSystemDao
    ): AssignmentRepository = AssignmentRepository(api, assignmentDao, subjectDao, syncStateDao, subjectRepository, srsSystemDao)

    @Provides
    @Singleton
    fun provideStatsRepository(
        api: WaniKaniApi,
        reviewStatisticDao: ReviewStatisticDao,
        levelProgressionDao: LevelProgressionDao,
        studyActivityDao: StudyActivityDao,
        syncStateDao: SyncStateDao
    ): StatsRepository = StatsRepository(api, reviewStatisticDao, levelProgressionDao, studyActivityDao, syncStateDao)

    @Provides
    @Singleton
    fun provideSettingsRepository(dataStore: DataStore<Preferences>): SettingsRepository =
        SettingsRepository(dataStore)

    @Provides
    @Singleton
    fun provideTokenRepository(dataStore: DataStore<Preferences>, tokenCipher: TokenCipher): TokenRepository =
        TokenRepository(dataStore, tokenCipher)

    @Provides
    @Singleton
    fun provideOutboxRepository(
        outboxDao: OutboxDao,
        outboxSyncScheduler: OutboxSyncScheduler,
        dataStore: DataStore<Preferences>
    ): OutboxRepository = OutboxRepository(outboxDao, outboxSyncScheduler, dataStore)
}

@Module
@InstallIn(SingletonComponent::class)
interface PitchAccentProviderModule {
    @Binds
    fun bindPitchAccentProvider(impl: PitchAccentRepository): PitchAccentProvider
}
