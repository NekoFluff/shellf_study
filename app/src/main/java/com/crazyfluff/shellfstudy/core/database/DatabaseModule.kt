package com.crazyfluff.shellfstudy.core.database

import android.content.Context
import androidx.room.Room
import com.crazyfluff.shellfstudy.core.database.outbox.OutboxDao
import com.crazyfluff.shellfstudy.core.database.outbox.OutboxDatabase
import com.crazyfluff.shellfstudy.core.database.pitchaccent.PitchAccentCacheDao
import com.crazyfluff.shellfstudy.core.database.pitchaccent.PitchAccentDatabase
import com.crazyfluff.shellfstudy.core.database.studyactivity.STUDY_ACTIVITY_MIGRATION_1_2
import com.crazyfluff.shellfstudy.core.database.studyactivity.StudyActivityDao
import com.crazyfluff.shellfstudy.core.database.studyactivity.StudyActivityDatabase
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
        Room.databaseBuilder(context, AppDatabase::class.java, "shellf_study.db")
            // Local cache only (subjects/assignments/etc. re-fetched from the API), so a
            // destructive migration on schema changes is simpler than hand-written Migration
            // objects.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

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
        Room.databaseBuilder(context, StudyActivityDatabase::class.java, "review_history.db")
            .addMigrations(STUDY_ACTIVITY_MIGRATION_1_2)
            .build()

    @Provides
    fun provideStudyActivityDao(db: StudyActivityDatabase): StudyActivityDao = db.studyActivityDao()

    @Provides
    @Singleton
    fun provideOutboxDatabase(@ApplicationContext context: Context): OutboxDatabase =
        // Not destructive — pending mutations must survive a schema bump (see OutboxDatabase's doc
        // comment). Version 1 has no back-compat burden yet, so no Migration is needed.
        Room.databaseBuilder(context, OutboxDatabase::class.java, "outbox.db")
            .build()

    @Provides
    fun provideOutboxDao(db: OutboxDatabase): OutboxDao = db.outboxDao()

    @Provides
    @Singleton
    fun providePitchAccentDatabase(@ApplicationContext context: Context): PitchAccentDatabase =
        // Re-derivable by re-scraping weblio.jp (see PitchAccentDatabase's doc comment), but a
        // destructive migration would force needless re-scraping, so this uses normal migrations.
        Room.databaseBuilder(context, PitchAccentDatabase::class.java, "pitch_accent.db")
            .build()

    @Provides
    fun providePitchAccentCacheDao(db: PitchAccentDatabase): PitchAccentCacheDao = db.pitchAccentCacheDao()
}
