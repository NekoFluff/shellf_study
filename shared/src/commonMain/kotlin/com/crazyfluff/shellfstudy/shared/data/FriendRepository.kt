package com.crazyfluff.shellfstudy.shared.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.crazyfluff.shellfstudy.shared.data.model.FriendEntry
import kotlinx.coroutines.CancellationException
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
    suspend fun addFriend(nickname: String, plainToken: String): FriendAdded {
        val entry = FriendEntry(
            id = Uuid.random().toString(),
            nickname = nickname,
            encryptedToken = tokenCipher.encrypt(plainToken)
        )
        val current = readRosterForWrite() ?: return FriendAdded.RosterUnreadable
        store.save(current + entry)
        return FriendAdded.Saved(entry)
    }

    suspend fun removeFriend(id: String): RosterWrite {
        val current = readRosterForWrite() ?: return RosterWrite.RosterUnreadable
        store.save(current.filter { it.id != id })
        return RosterWrite.Saved
    }

    suspend fun updateNickname(id: String, nickname: String): RosterWrite {
        val current = readRosterForWrite() ?: return RosterWrite.RosterUnreadable
        store.save(current.map { if (it.id == id) it.copy(nickname = nickname) else it })
        return RosterWrite.Saved
    }

    /**
     * The roster a mutation must start from — an empty list for an absent key (the ordinary
     * first-add case), or null when the stored roster is present but can't be decoded, in which case
     * the caller must not write at all.
     *
     * The distinction is the whole point: [friendsFlow] maps an unreadable roster to an empty one so
     * the screen has something to render, and a mutation that reuses that reading would save over
     * the raw value with a roster built from the empty one — losing every friend the user had, with
     * no error anywhere. Refusing the write keeps the bytes intact so a later build (or a fix) can
     * still read them.
     */
    private suspend fun readRosterForWrite(): List<FriendEntry>? =
        try {
            store.loadOrThrow().orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    private companion object {
        const val FRIENDS_KEY_NAME = "friend_entries"
    }
}

/** Outcome of [FriendRepository.addFriend]. */
sealed interface FriendAdded {
    data class Saved(val entry: FriendEntry) : FriendAdded

    /** The stored roster couldn't be decoded, so nothing was written — see [RosterWrite]. */
    data object RosterUnreadable : FriendAdded
}

/**
 * Outcome of a roster mutation that has no value to hand back ([FriendRepository.removeFriend],
 * [FriendRepository.updateNickname]).
 */
sealed interface RosterWrite {
    data object Saved : RosterWrite

    /**
     * The stored roster couldn't be decoded, so storage was left exactly as it was rather than
     * replaced by a roster built from an empty read.
     */
    data object RosterUnreadable : RosterWrite
}
