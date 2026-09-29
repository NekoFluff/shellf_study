package com.crazyfluff.shellfstudy.shared.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.concurrent.Volatile

/** Stores the user's WaniKani API token, encrypted at rest via [TokenCipher]. */
class TokenRepository(
    private val dataStore: DataStore<Preferences>,
    private val tokenCipher: TokenCipher
) {
    private val encryptedTokenKey = stringPreferencesKey("encrypted_api_token")

    // dataStore is a single app-wide DataStore<Preferences> shared by every repository that
    // persists key-value state, so dataStore.data re-emits on every write to that file regardless
    // of which key changed — distinctUntilChanged() keeps an unrelated write elsewhere from
    // re-triggering every collector of this flow (see SettingsRepository.settings for the fuller
    // account of this, including the dropped-frames incident it caused there).
    /** Emits the current (decrypted) token, or null if none is stored. */
    val tokenFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[encryptedTokenKey]?.let(::decrypt)
    }.distinctUntilChanged()

    // Every HTTP request reads the token (see AuthTokenProvider), and decrypting it is a Keystore or
    // Keychain round trip each time. Keyed by the ciphertext, so saving or clearing the token can
    // never serve the old one. A failed decrypt is not remembered, so the next read tries again.
    @Volatile
    private var lastDecryption: Pair<String, String>? = null

    private fun decrypt(encrypted: String): String? {
        lastDecryption?.let { (cachedFor, token) -> if (cachedFor == encrypted) return token }
        val token = runCatching { tokenCipher.decrypt(encrypted) }.getOrNull() ?: return null
        lastDecryption = encrypted to token
        return token
    }

    suspend fun saveToken(token: String) {
        val encrypted = tokenCipher.encrypt(token)
        dataStore.edit { prefs -> prefs[encryptedTokenKey] = encrypted }
    }

    suspend fun clearToken() {
        dataStore.edit { prefs -> prefs.remove(encryptedTokenKey) }
        tokenCipher.clear()
    }
}
