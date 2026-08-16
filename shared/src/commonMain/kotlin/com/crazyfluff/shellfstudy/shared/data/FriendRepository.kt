package com.crazyfluff.shellfstudy.shared.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.crazyfluff.shellfstudy.shared.data.model.FriendEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class FriendRepository(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
    private val tokenCipher: TokenCipher
) {
    private val store = JsonPreferenceStore(
        dataStore, json, FRIENDS_KEY_NAME,
        ListSerializer(FriendEntry.serializer())
    )

    // Read directly from DataStore's preference key to avoid calling store.load() (which calls
    // dataStore.data.first() internally) inside a map on dataStore.data — that would deadlock.
    private val friendsKey = stringPreferencesKey(FRIENDS_KEY_NAME)

    val friendsFlow: Flow<List<FriendEntry>> = dataStore.data
        .map { prefs ->
            prefs[friendsKey]?.let { raw ->
                runCatching {
                    json.decodeFromString(ListSerializer(FriendEntry.serializer()), raw)
                }.getOrNull()
            } ?: emptyList()
        }
        .distinctUntilChanged()

    fun decryptToken(entry: FriendEntry): String = tokenCipher.decrypt(entry.encryptedToken)

    @OptIn(ExperimentalUuidApi::class)
    suspend fun addFriend(nickname: String, plainToken: String): FriendEntry {
        val entry = FriendEntry(
            id = Uuid.random().toString(),
            nickname = nickname,
            encryptedToken = tokenCipher.encrypt(plainToken)
        )
        val current = store.load() ?: emptyList()
        store.save(current + entry)
        return entry
    }

    suspend fun removeFriend(id: String) {
        val current = store.load() ?: emptyList()
        store.save(current.filter { it.id != id })
    }

    suspend fun updateNickname(id: String, nickname: String) {
        val current = store.load() ?: emptyList()
        store.save(current.map { if (it.id == id) it.copy(nickname = nickname) else it })
    }

    private companion object {
        const val FRIENDS_KEY_NAME = "friend_entries"
    }
}
