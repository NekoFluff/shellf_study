package com.crazyfluff.shellfstudy.shared.database.studyactivity

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
 * Isolated from [com.crazyfluff.shellfstudy.shared.database.AppDatabase] on purpose: that database is
 * a disposable cache re-derived from the API on every sync (see its destructive-migration
 * comment), but which calendar days had study activity is NOT re-derivable — WaniKani's
 * `GET /v2/reviews` endpoint is deprecated and always returns empty data, so this table is the
 * only local record of when the user was active. Keeping it in its own tiny database means the
 * fast-evolving cache schema can keep changing destructively without ever risking this one
 * irreplaceable (if small) table.
 *
 * Previously stored one row per completed review (assignment id, SRS stage transition, incorrect
 * counts) as `review_log`/`ReviewLogEntity`. Trimmed down to just the set of active days: the only
 * consumer of any of that detail was the daily study-reminder notification, and it only ever read
 * two derived values — whether today had activity, and the current day streak — never anything
 * per-review.
 */
@Database(entities = [StudyActivityDayEntity::class], version = 2, exportSchema = true)
@ConstructedBy(StudyActivityDatabaseConstructor::class)
abstract class StudyActivityDatabase : RoomDatabase() {
    abstract fun studyActivityDao(): StudyActivityDao
}

@Suppress("KotlinNoActualForExpect")
expect object StudyActivityDatabaseConstructor : RoomDatabaseConstructor<StudyActivityDatabase> {
    override fun initialize(): StudyActivityDatabase
}

internal const val STUDY_ACTIVITY_DATABASE_FILE_NAME = "review_history.db"

/** Backfills active-day markers from the old per-review log before dropping it, so existing users'
 *  streaks survive the upgrade instead of resetting to zero. */
val STUDY_ACTIVITY_MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `study_activity_days` (`date` TEXT NOT NULL, PRIMARY KEY(`date`))")
        connection.execSQL(
            "INSERT OR IGNORE INTO study_activity_days (date) " +
                "SELECT DISTINCT substr(reviewedAt, 1, 10) FROM review_log"
        )
        connection.execSQL("DROP TABLE IF EXISTS review_log")
    }
}

fun buildStudyActivityDatabase(builder: RoomDatabase.Builder<StudyActivityDatabase>): StudyActivityDatabase =
    builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        .addMigrations(STUDY_ACTIVITY_MIGRATION_1_2)
        .build()
