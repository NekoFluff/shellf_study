package com.crazyfluff.shellfstudy.core.database.outbox

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Durable outbox of network mutations queued while offline (review submissions, lesson starts) —
 * kept in its own database rather than [com.crazyfluff.shellfstudy.core.database.AppDatabase]
 * (a disposable cache, see its destructive-migration comment) because a pending mutation must
 * survive a schema bump. Distinct from the studyactivity database too: rows here are transient
 * and deleted the moment they sync, unlike a permanent record.
 */
@Database(entities = [PendingReviewSubmissionEntity::class, PendingLessonStartEntity::class], version = 1, exportSchema = true)
abstract class OutboxDatabase : RoomDatabase() {
    abstract fun outboxDao(): OutboxDao
}
