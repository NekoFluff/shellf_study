package com.crazyfluff.shellfstudy.core.database.reviewhistory

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Isolated from [com.crazyfluff.shellfstudy.core.database.AppDatabase] on purpose: that database is
 * a disposable cache re-derived from the API on every sync (see its destructive-migration
 * comment), but review history is NOT re-derivable — WaniKani's `GET /v2/reviews` endpoint is
 * deprecated and always returns empty data, so this table is the only record of a user's review
 * activity. Keeping it in its own tiny database means the fast-evolving cache schema can keep
 * changing destructively without ever risking this one irreplaceable table.
 */
@Database(entities = [ReviewLogEntity::class], version = 1, exportSchema = true)
abstract class ReviewHistoryDatabase : RoomDatabase() {
    abstract fun reviewLogDao(): ReviewLogDao
}
