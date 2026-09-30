package com.crazyfluff.shellfstudy.shared.database.studytime

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

/**
 * Kept apart from [com.crazyfluff.shellfstudy.shared.database.AppDatabase] for the same reason as
 * [com.crazyfluff.shellfstudy.shared.database.studyactivity.StudyActivityDatabase]: the WaniKani API
 * has no record of how long anyone studied, so these rows can't be re-derived and must never sit
 * behind a destructive migration.
 */
@Database(entities = [StudyTimeSegmentEntity::class], version = 1, exportSchema = true)
@ConstructedBy(StudyTimeDatabaseConstructor::class)
abstract class StudyTimeDatabase : RoomDatabase() {
    abstract fun studyTimeDao(): StudyTimeDao
}

@Suppress("KotlinNoActualForExpect")
expect object StudyTimeDatabaseConstructor : RoomDatabaseConstructor<StudyTimeDatabase> {
    override fun initialize(): StudyTimeDatabase
}

internal const val STUDY_TIME_DATABASE_FILE_NAME = "study_time.db"

fun buildStudyTimeDatabase(builder: RoomDatabase.Builder<StudyTimeDatabase>): StudyTimeDatabase =
    builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        .build()
