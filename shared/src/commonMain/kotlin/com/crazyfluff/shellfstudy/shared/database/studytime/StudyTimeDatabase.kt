package com.crazyfluff.shellfstudy.shared.database.studytime

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers

/**
 * Kept apart from [com.crazyfluff.shellfstudy.shared.database.AppDatabase] for the same reason as
 * [com.crazyfluff.shellfstudy.shared.database.studyactivity.StudyActivityDatabase]: the WaniKani API
 * has no record of how long anyone studied, so these rows can't be re-derived and must never sit
 * behind a destructive migration.
 */
@Database(entities = [StudyTimeSegmentEntity::class], version = 2, exportSchema = true)
@ConstructedBy(StudyTimeDatabaseConstructor::class)
abstract class StudyTimeDatabase : RoomDatabase() {
    abstract fun studyTimeDao(): StudyTimeDao
}

@Suppress("KotlinNoActualForExpect")
expect object StudyTimeDatabaseConstructor : RoomDatabaseConstructor<StudyTimeDatabase> {
    override fun initialize(): StudyTimeDatabase
}

internal const val STUDY_TIME_DATABASE_FILE_NAME = "study_time.db"

/**
 * Gives every existing stretch a [StudyTimeSegmentEntity.syncId] and marks it not yet uploaded, so the
 * first history sync backfills everything recorded before sync existed. SQLite has no UUID function,
 * so backfilled ids are 32 random hex digits rather than [kotlin.uuid.Uuid]'s dashed form; both are
 * opaque ids to the backend.
 */
val STUDY_TIME_MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `study_time_segments` ADD COLUMN `syncId` TEXT NOT NULL DEFAULT ''")
        connection.execSQL("ALTER TABLE `study_time_segments` ADD COLUMN `uploaded` INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("UPDATE `study_time_segments` SET `syncId` = lower(hex(randomblob(16))) WHERE `syncId` = ''")
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_study_time_segments_syncId` ON `study_time_segments` (`syncId`)"
        )
    }
}

fun buildStudyTimeDatabase(builder: RoomDatabase.Builder<StudyTimeDatabase>): StudyTimeDatabase =
    builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        .addMigrations(STUDY_TIME_MIGRATION_1_2)
        .build()
