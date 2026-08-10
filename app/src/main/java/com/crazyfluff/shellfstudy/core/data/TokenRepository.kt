package com.crazyfluff.shellfstudy.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Stores the user's WaniKani API token, encrypted at rest via [TokenCipher]. */
@Singleton
class TokenRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val tokenCipher: TokenCipher
) {
    private val encryptedTokenKey = stringPreferencesKey("encrypted_api_token")

    /** Emits the current (decrypted) token, or null if none is stored. */
    val tokenFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[encryptedTokenKey]?.let { encrypted ->
            runCatching { tokenCipher.decrypt(encrypted) }.getOrNull()
        }
    }

    suspend fun saveToken(token: String) {
        val encrypted = tokenCipher.encrypt(token)
        dataStore.edit { prefs -> prefs[encryptedTokenKey] = encrypted }
    }

    suspend fun clearToken() {
        dataStore.edit { prefs -> prefs.remove(encryptedTokenKey) }
    }
}
