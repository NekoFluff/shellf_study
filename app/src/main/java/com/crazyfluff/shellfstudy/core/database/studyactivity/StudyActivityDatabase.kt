package com.crazyfluff.shellfstudy.core.database.studyactivity

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Isolated from [com.crazyfluff.shellfstudy.core.database.AppDatabase] on purpose: that database is
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
abstract class StudyActivityDatabase : RoomDatabase() {
    abstract fun studyActivityDao(): StudyActivityDao
}

/** Backfills active-day markers from the old per-review log before dropping it, so existing users'
 *  streaks survive the upgrade instead of resetting to zero. */
val STUDY_ACTIVITY_MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `study_activity_days` (`date` TEXT NOT NULL, PRIMARY KEY(`date`))")
        db.execSQL(
            "INSERT OR IGNORE INTO study_activity_days (date) " +
                "SELECT DISTINCT substr(reviewedAt, 1, 10) FROM review_log"
        )
        db.execSQL("DROP TABLE IF EXISTS review_log")
    }
}
