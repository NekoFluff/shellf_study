package com.crazyfluff.shellfstudy.shared.database.outbox

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

/**
 * Durable outbox of network mutations queued while offline (review submissions, lesson starts) —
 * kept in its own database rather than [com.crazyfluff.shellfstudy.shared.database.AppDatabase]
 * (a disposable cache, see its destructive-migration comment) because a pending mutation must
 * survive a schema bump. Distinct from the studyactivity database too: rows here are transient
 * and deleted the moment they sync, unlike a permanent record.
 */
@Database(entities = [PendingReviewSubmissionEntity::class, PendingLessonStartEntity::class], version = 1, exportSchema = true)
@ConstructedBy(OutboxDatabaseConstructor::class)
abstract class OutboxDatabase : RoomDatabase() {
    abstract fun outboxDao(): OutboxDao
}

@Suppress("KotlinNoActualForExpect")
expect object OutboxDatabaseConstructor : RoomDatabaseConstructor<OutboxDatabase> {
    override fun initialize(): OutboxDatabase
}

internal const val OUTBOX_DATABASE_FILE_NAME = "outbox.db"

fun buildOutboxDatabase(builder: RoomDatabase.Builder<OutboxDatabase>): OutboxDatabase =
    builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        .build()
