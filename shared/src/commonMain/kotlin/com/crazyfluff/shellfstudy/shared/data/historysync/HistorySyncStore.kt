package com.crazyfluff.shellfstudy.shared.data.historysync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.crazyfluff.shellfstudy.shared.data.TokenCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** A token for the history-sync backend, and the WaniKani user it was issued for. */
data class HistorySyncSession(val token: String, val userId: String, val expiresAtMs: Long)

/**
 * History sync's persisted state: whether the learner turned it on, how far this device has pulled,
 * and the backend token, encrypted at rest with the same [TokenCipher] as the WaniKani token.
 */
class HistorySyncStore(
    private val dataStore: DataStore<Preferences>,
    private val tokenCipher: TokenCipher
) {
    private val enabledKey = booleanPreferencesKey("history_sync_enabled")
    private val cursorKey = longPreferencesKey("history_sync_cursor")
    private val encryptedTokenKey = stringPreferencesKey("history_sync_encrypted_token")
    private val tokenUserIdKey = stringPreferencesKey("history_sync_token_user_id")
    private val tokenExpiresAtKey = longPreferencesKey("history_sync_token_expires_at_ms")
    private val lastSyncedAtKey = longPreferencesKey("history_sync_last_synced_at_ms")

    val enabled: Flow<Boolean> = dataStore.data.map { it[enabledKey] ?: false }.distinctUntilChanged()

    suspend fun isEnabled(): Boolean = enabled.first()

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[enabledKey] = enabled }
    }

    /** When a pass last finished cleanly, or null if none has yet for this account. */
    val lastSyncedAtMs: Flow<Long?> = dataStore.data.map { it[lastSyncedAtKey] }.distinctUntilChanged()

    suspend fun setLastSyncedAt(atMs: Long) {
        dataStore.edit { it[lastSyncedAtKey] = atMs }
    }

    suspend fun cursor(): Long = dataStore.data.first()[cursorKey] ?: 0L

    suspend fun setCursor(cursor: Long) {
        dataStore.edit { it[cursorKey] = cursor }
    }

    /** The stored session, or null if there is none or it no longer decrypts (a cleared key, say). */
    suspend fun session(): HistorySyncSession? {
        val prefs = dataStore.data.first()
        val token = prefs[encryptedTokenKey]?.let { runCatching { tokenCipher.decrypt(it) }.getOrNull() }
        val userId = prefs[tokenUserIdKey]
        val expiresAtMs = prefs[tokenExpiresAtKey]
        return if (token != null && userId != null && expiresAtMs != null) {
            HistorySyncSession(token, userId, expiresAtMs)
        } else {
            null
        }
    }

    suspend fun saveSession(session: HistorySyncSession) {
        val encrypted = tokenCipher.encrypt(session.token)
        dataStore.edit {
            it[encryptedTokenKey] = encrypted
            it[tokenUserIdKey] = session.userId
            it[tokenExpiresAtKey] = session.expiresAtMs
        }
    }

    suspend fun clearSession() {
        dataStore.edit {
            it.remove(encryptedTokenKey)
            it.remove(tokenUserIdKey)
            it.remove(tokenExpiresAtKey)
        }
    }

    /**
     * Forgets everything tied to the account whose history this device held: the token, the pull
     * cursor, and the choice to sync, which that person made and the next one hasn't.
     */
    suspend fun clearAccountState() {
        dataStore.edit {
            it.remove(enabledKey)
            it.remove(cursorKey)
            it.remove(lastSyncedAtKey)
            it.remove(encryptedTokenKey)
            it.remove(tokenUserIdKey)
            it.remove(tokenExpiresAtKey)
        }
    }
}
