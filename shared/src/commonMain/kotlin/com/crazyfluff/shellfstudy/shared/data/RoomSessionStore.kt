package com.crazyfluff.shellfstudy.shared.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.crazyfluff.shellfstudy.shared.database.session.SessionDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Room-backed persistence for a single in-progress session, the replacement for
 * [JsonPreferenceStore] on the session paths. See
 * [com.crazyfluff.shellfstudy.shared.database.session.SessionDatabase] for why sessions moved out of
 * the shared preferences DataStore.
 *
 * [legacyDataStore] exists only to drain sessions written before that move, and is expected to be
 * null once no install can still be carrying one. It is a constructor dependency rather than a
 * separate migration step so every entry point (`save`, `load`, `hasActiveSession`, `clear`) is
 * consistent about which store is authoritative — a half-migrated state where a read sees the Room
 * copy and a write updates the DataStore one is exactly the kind of thing that produces a session
 * that silently will not resume.
 */
class RoomSessionStore<T>(
    private val sessionDao: SessionDao,
    private val sessionKey: String,
    private val serializer: KSerializer<T>,
    private val json: Json,
    private val legacyDataStore: DataStore<Preferences>?,
    private val legacyKeyName: String
) {
    private val legacyKey = stringPreferencesKey(legacyKeyName)

    /**
     * Whether a resumable session exists. Deliberately does not migrate first: this is collected by
     * the dashboard for its "Resume" affordance, and a cold start would otherwise trigger a
     * DataStore read on the composition path. [load] performs the migration, and the dashboard only
     * offers Resume for a session the review/lesson screen will actually be able to load.
     */
    val exists: Flow<Boolean> = sessionDao.observeExists(sessionKey).map { it }

    suspend fun save(value: T) {
        sessionDao.upsert(
            com.crazyfluff.shellfstudy.shared.database.session.PersistedSessionEntity(
                sessionKey = sessionKey,
                payload = json.encodeToString(serializer, value)
            )
        )
    }

    /**
     * Reads the session, migrating a pre-existing DataStore one on the way through.
     *
     * The migration is idempotent and runs before every read rather than once per process: it is a
     * single `prefs[legacyKey]` lookup on a DataStore that is already open, and doing it here means
     * a crash between the two steps leaves the session recoverable from the legacy key instead of
     * losing it. The legacy key is only cleared after the Room write has returned.
     */
    suspend fun load(): T? {
        migrateLegacyIfPresent()
        val raw = sessionDao.getPayload(sessionKey) ?: return null
        return runCatching { json.decodeFromString(serializer, raw) }.getOrNull()
    }

    suspend fun clear() {
        // A tombstone, not a delete — see SessionDao.upsert.
        sessionDao.upsert(
            com.crazyfluff.shellfstudy.shared.database.session.PersistedSessionEntity(
                sessionKey = sessionKey,
                payload = null
            )
        )
        legacyDataStore?.edit { prefs -> prefs.remove(legacyKey) }
    }

    /**
     * Copies a session left in DataStore by a previous version into Room, then clears the legacy key.
     *
     * Ordering matters: the Room write completes before the legacy key is removed, so a failure or
     * process death between them leaves the session readable from DataStore and the next call retries.
     * A payload that fails to decode is still migrated verbatim — the store does not interpret what
     * it moves, and `load`'s own `runCatching` is what decides that a stored value is unreadable.
     */
    private suspend fun migrateLegacyIfPresent() {
        val store = legacyDataStore ?: return
        val legacyRaw = store.data.first()[legacyKey] ?: return
        sessionDao.upsert(
            com.crazyfluff.shellfstudy.shared.database.session.PersistedSessionEntity(
                sessionKey = sessionKey,
                payload = legacyRaw
            )
        )
        store.edit { prefs -> prefs.remove(legacyKey) }
    }
}
