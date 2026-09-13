package com.crazyfluff.shellfstudy.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.crazyfluff.shellfstudy.MainDispatcherRule
import com.crazyfluff.shellfstudy.fakes.FakeTokenCipher
import com.crazyfluff.shellfstudy.shared.data.FriendAdded
import com.crazyfluff.shellfstudy.shared.data.FriendRepository
import com.crazyfluff.shellfstudy.shared.data.RosterWrite
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Covers the friends roster's read-modify-write path.
 *
 * The regression this guards: every mutator read the roster through a fold that maps "can't decode"
 * to "empty", then saved the edit on top of that empty list. So a roster the app failed to parse — a
 * partial write, or a field renamed by a later schema — was silently replaced on the next add,
 * remove, or rename by a list containing only that edit. A mutation that cannot read what is already
 * on disk has to refuse and leave the stored bytes alone.
 */
class FriendRepositoryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: FriendRepository

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(mainDispatcherRule.dispatcher + SupervisorJob()),
            produceFile = { tempFolder.newFile("test.preferences_pb") }
        )
        repository = FriendRepository(dataStore, Json { ignoreUnknownKeys = true }, FakeTokenCipher())
    }

    @Test
    fun `addFriend saves when nothing is stored yet`() = runTest(mainDispatcherRule.dispatcher) {
        val result = repository.addFriend("rival", "their-token")

        assertThat(result).isInstanceOf(FriendAdded.Saved::class.java)
        assertThat(repository.friendsFlow.first().map { it.nickname }).containsExactly("rival")
    }

    @Test
    fun `removeFriend and updateNickname save when the roster is readable`() =
        runTest(mainDispatcherRule.dispatcher) {
            val entry = (repository.addFriend("old", "their-token") as FriendAdded.Saved).entry

            assertThat(repository.updateNickname(entry.id, "new")).isEqualTo(RosterWrite.Saved)
            assertThat(repository.friendsFlow.first().single().nickname).isEqualTo("new")

            assertThat(repository.removeFriend(entry.id)).isEqualTo(RosterWrite.Saved)
            assertThat(repository.friendsFlow.first()).isEmpty()
        }

    @Test
    fun `addFriend refuses to overwrite an unreadable roster`() =
        runTest(mainDispatcherRule.dispatcher) {
            seedUnreadableRoster()

            val result = repository.addFriend("rival", "their-token")

            assertThat(result).isEqualTo(FriendAdded.RosterUnreadable)
            assertThat(storedRosterRaw()).isEqualTo(UNREADABLE_ROSTER)
        }

    @Test
    fun `removeFriend refuses to overwrite an unreadable roster`() =
        runTest(mainDispatcherRule.dispatcher) {
            seedUnreadableRoster()

            val result = repository.removeFriend("some-id")

            assertThat(result).isEqualTo(RosterWrite.RosterUnreadable)
            assertThat(storedRosterRaw()).isEqualTo(UNREADABLE_ROSTER)
        }

    @Test
    fun `updateNickname refuses to overwrite an unreadable roster`() =
        runTest(mainDispatcherRule.dispatcher) {
            seedUnreadableRoster()

            val result = repository.updateNickname("some-id", "New Name")

            assertThat(result).isEqualTo(RosterWrite.RosterUnreadable)
            assertThat(storedRosterRaw()).isEqualTo(UNREADABLE_ROSTER)
        }

    private suspend fun seedUnreadableRoster() {
        dataStore.edit { prefs -> prefs[FRIENDS_KEY] = UNREADABLE_ROSTER }
    }

    private suspend fun storedRosterRaw(): String? = dataStore.data.first()[FRIENDS_KEY]

    private companion object {
        /** `JsonPreferenceStore`'s private key name for the roster. */
        val FRIENDS_KEY = stringPreferencesKey("friend_entries")

        /** Valid JSON of the wrong shape — what a schema change or a partial write leaves behind. */
        const val UNREADABLE_ROSTER = """[{"unexpected":"field"}]"""
    }
}
