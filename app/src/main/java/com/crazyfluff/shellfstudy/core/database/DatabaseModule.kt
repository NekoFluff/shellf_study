package com.crazyfluff.shellfstudy.core.database

import android.content.Context
import androidx.room.Room
import com.crazyfluff.shellfstudy.core.database.pitchaccent.PitchAccentCacheDao
import com.crazyfluff.shellfstudy.core.database.pitchaccent.PitchAccentDatabase
import com.crazyfluff.shellfstudy.core.database.reviewhistory.ReviewHistoryDatabase
import com.crazyfluff.shellfstudy.core.database.reviewhistory.ReviewLogDao
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
    fun provideReviewHistoryDatabase(@ApplicationContext context: Context): ReviewHistoryDatabase =
        // Not destructive — this is the only local record of review history and can't be
        // re-fetched from the API (see ReviewHistoryDatabase's doc comment).
        Room.databaseBuilder(context, ReviewHistoryDatabase::class.java, "review_history.db")
            .build()

    @Provides
    fun provideReviewLogDao(db: ReviewHistoryDatabase): ReviewLogDao = db.reviewLogDao()

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
