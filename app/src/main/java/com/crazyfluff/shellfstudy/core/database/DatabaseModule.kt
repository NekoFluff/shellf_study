package com.crazyfluff.shellfstudy.core.database

import android.content.Context
import com.crazyfluff.shellfstudy.shared.database.AppDatabase
import com.crazyfluff.shellfstudy.shared.database.AssignmentDao
import com.crazyfluff.shellfstudy.shared.database.LevelProgressionDao
import com.crazyfluff.shellfstudy.shared.database.ReviewStatisticDao
import com.crazyfluff.shellfstudy.shared.database.SrsSystemDao
import com.crazyfluff.shellfstudy.shared.database.StudyMaterialDao
import com.crazyfluff.shellfstudy.shared.database.SubjectDao
import com.crazyfluff.shellfstudy.shared.database.SyncStateDao
import com.crazyfluff.shellfstudy.shared.database.buildAppDatabase
import com.crazyfluff.shellfstudy.shared.database.getAppDatabaseBuilder
import com.crazyfluff.shellfstudy.shared.database.outbox.OutboxDao
import com.crazyfluff.shellfstudy.shared.database.outbox.OutboxDatabase
import com.crazyfluff.shellfstudy.shared.database.outbox.buildOutboxDatabase
import com.crazyfluff.shellfstudy.shared.database.outbox.getOutboxDatabaseBuilder
import com.crazyfluff.shellfstudy.shared.database.pitchaccent.PitchAccentCacheDao
import com.crazyfluff.shellfstudy.shared.database.pitchaccent.PitchAccentDatabase
import com.crazyfluff.shellfstudy.shared.database.pitchaccent.buildPitchAccentDatabase
import com.crazyfluff.shellfstudy.shared.database.pitchaccent.getPitchAccentDatabaseBuilder
import com.crazyfluff.shellfstudy.shared.database.studyactivity.StudyActivityDao
import com.crazyfluff.shellfstudy.shared.database.studyactivity.StudyActivityDatabase
import com.crazyfluff.shellfstudy.shared.database.studyactivity.buildStudyActivityDatabase
import com.crazyfluff.shellfstudy.shared.database.studyactivity.getStudyActivityDatabaseBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        buildAppDatabase(getAppDatabaseBuilder(context))

    @Provides
    fun provideSubjectDao(db: AppDatabase): SubjectDao = db.subjectDao()

    @Provides
    fun provideAssignmentDao(db: AppDatabase): AssignmentDao = db.assignmentDao()

    @Provides
    fun provideSrsSystemDao(db: AppDatabase): SrsSystemDao = db.srsSystemDao()

    @Provides
    fun provideReviewStatisticDao(db: AppDatabase): ReviewStatisticDao = db.reviewStatisticDao()

    @Provides
    fun provideStudyMaterialDao(db: AppDatabase): StudyMaterialDao = db.studyMaterialDao()

    @Provides
    fun provideLevelProgressionDao(db: AppDatabase): LevelProgressionDao = db.levelProgressionDao()

    @Provides
    fun provideSyncStateDao(db: AppDatabase): SyncStateDao = db.syncStateDao()

    @Provides
    @Singleton
    fun provideStudyActivityDatabase(@ApplicationContext context: Context): StudyActivityDatabase =
        // Not destructive — this is the only local record of study activity and can't be
        // re-fetched from the API (see StudyActivityDatabase's doc comment). Physical file name is
        // unchanged from this database's original review-history-log incarnation, so the
        // Migration(1, 2) that shrinks it runs in place rather than needing a cross-file move.
        buildStudyActivityDatabase(getStudyActivityDatabaseBuilder(context))

    @Provides
    fun provideStudyActivityDao(db: StudyActivityDatabase): StudyActivityDao = db.studyActivityDao()

    @Provides
    @Singleton
    fun provideOutboxDatabase(@ApplicationContext context: Context): OutboxDatabase =
        // Not destructive — pending mutations must survive a schema bump (see OutboxDatabase's doc
        // comment). Version 1 has no back-compat burden yet, so no Migration is needed.
        buildOutboxDatabase(getOutboxDatabaseBuilder(context))

    @Provides
    fun provideOutboxDao(db: OutboxDatabase): OutboxDao = db.outboxDao()

    @Provides
    @Singleton
    fun providePitchAccentDatabase(@ApplicationContext context: Context): PitchAccentDatabase =
        // Re-derivable by re-scraping weblio.jp (see PitchAccentDatabase's doc comment), but a
        // destructive migration would force needless re-scraping, so this uses normal migrations.
        buildPitchAccentDatabase(getPitchAccentDatabaseBuilder(context))

    @Provides
    fun providePitchAccentCacheDao(db: PitchAccentDatabase): PitchAccentCacheDao = db.pitchAccentCacheDao()
}
