package com.crazyfluff.shellfstudy.shared.database.session

import androidx.room.ConstructedBy
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

/**
 * An in-progress review or lesson session, stored as the JSON blob its repository already
 * serializes. A null [payload] is a tombstone, not an absent row — see [SessionDao.upsert].
 */
@Entity(tableName = "persisted_sessions")
data class PersistedSessionEntity(
    /** [REVIEW_SESSION_KEY] or [LESSON_SESSION_KEY]. */
    @PrimaryKey val sessionKey: String,
    val payload: String?
)

const val REVIEW_SESSION_KEY = "review"
const val LESSON_SESSION_KEY = "lesson"

@Dao
interface SessionDao {
    /**
     * Writes a session, or a tombstone when [payload] is null.
     *
     * Clearing writes a row with a null payload rather than deleting the row, because observers are
     * driven by Room's invalidation tracker: a DELETE that removes the last matching row still
     * invalidates the table, but a subsequent `observeExists` that receives no *new* row can leave a
     * collector that has already been disposed and re-created reading a stale value. Keeping the row
     * present with a null payload means every write produces the same shape of change and the
     * `payload IS NOT NULL` predicate below re-evaluates unambiguously.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: PersistedSessionEntity)

    @Query("SELECT payload FROM persisted_sessions WHERE sessionKey = :sessionKey")
    suspend fun getPayload(sessionKey: String): String?

    @Query("SELECT EXISTS(SELECT 1 FROM persisted_sessions WHERE sessionKey = :sessionKey AND payload IS NOT NULL)")
    fun observeExists(sessionKey: String): Flow<Boolean>

    @Query("DELETE FROM persisted_sessions")
    suspend fun clearAll()
}

/**
 * In-progress session state, in its own database.
 *
 * Sessions used to live as JSON blobs under two keys of the app-wide `DataStore<Preferences>` — the
 * same file that holds the API token, settings, the dashboard cache and the friend roster. DataStore
 * has no per-key granularity: every write re-serializes and rewrites the whole preferences proto, and
 * re-emits `dataStore.data` to *every* collector. Since a review session persists on every graded
 * answer, each answer rewrote the entire app's settings file (25.6 KB measured on-device) and woke
 * `TokenRepository`, `SettingsRepository`, `DashboardCacheRepository`, `OutboxRepository.blockedOnAuth`,
 * `LastSessionSummaryRepository` and `NotificationStateRepository` to re-read it.
 *
 * A separate database also keeps a session write from invalidating the main database's observers, and
 * keeps session state out of the settings file it never belonged in. It is deliberately its own file
 * rather than a table in [com.crazyfluff.shellfstudy.shared.database.AppDatabase]: that database is a
 * disposable cache that falls back to a destructive migration, and an in-progress session must survive
 * a schema bump — the same reasoning as the outbox.
 */
@Database(entities = [PersistedSessionEntity::class], version = 1, exportSchema = true)
@ConstructedBy(SessionDatabaseConstructor::class)
abstract class SessionDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
}

@Suppress("KotlinNoActualForExpect")
expect object SessionDatabaseConstructor : RoomDatabaseConstructor<SessionDatabase> {
    override fun initialize(): SessionDatabase
}

internal const val SESSION_DATABASE_FILE_NAME = "sessions.db"

fun buildSessionDatabase(builder: RoomDatabase.Builder<SessionDatabase>): SessionDatabase =
    builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        .build()
