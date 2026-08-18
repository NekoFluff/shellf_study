package com.crazyfluff.shellfstudy.feature.leaderboard

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.crazyfluff.shellfstudy.MainDispatcherRule
import com.crazyfluff.shellfstudy.fakes.FakeAssignmentDao
import com.crazyfluff.shellfstudy.fakes.FakeFriendStatsDao
import com.crazyfluff.shellfstudy.fakes.FakeLevelProgressionDao
import com.crazyfluff.shellfstudy.fakes.FakeReviewStatisticDao
import com.crazyfluff.shellfstudy.fakes.FakeTokenCipher
import com.crazyfluff.shellfstudy.shared.data.FriendRepository
import com.crazyfluff.shellfstudy.shared.data.FriendStatsRepository
import com.crazyfluff.shellfstudy.shared.feature.leaderboard.LeaderboardViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LeaderboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var friendRepository: FriendRepository

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(mainDispatcherRule.dispatcher + SupervisorJob()),
            produceFile = { tempFolder.newFile("test.preferences_pb") }
        )
        val json = Json { ignoreUnknownKeys = true }
        friendRepository = FriendRepository(dataStore, json, FakeTokenCipher())
    }

    private fun createViewModel(): LeaderboardViewModel {
        val json = Json { ignoreUnknownKeys = true }
        val friendStatsRepository = FriendStatsRepository(
            friendRepository = friendRepository,
            friendStatsDao = FakeFriendStatsDao(),
            json = json,
            selfAssignmentDao = FakeAssignmentDao(),
            selfReviewStatisticDao = FakeReviewStatisticDao(),
            selfLevelProgressionDao = FakeLevelProgressionDao(),
            defaultDispatcher = mainDispatcherRule.dispatcher
        )
        return LeaderboardViewModel(friendRepository, friendStatsRepository, json)
    }

    @Test
    fun `onEditNickname updates the friend's nickname`() = runTest(mainDispatcherRule.dispatcher) {
        val entry = friendRepository.addFriend("Old Name", "some-token")
        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.friends.none { it.id == entry.id }) state = awaitItem()

            viewModel.onEditNickname(entry.id, "New Name")
            while (state.friends.first { it.id == entry.id }.nickname != "New Name") state = awaitItem()

            assertThat(state.friends.first { it.id == entry.id }.nickname).isEqualTo("New Name")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onEditNickname ignores a blank nickname`() = runTest(mainDispatcherRule.dispatcher) {
        val entry = friendRepository.addFriend("Old Name", "some-token")
        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.friends.none { it.id == entry.id }) state = awaitItem()

            viewModel.onEditNickname(entry.id, "   ")

            friendRepository.friendsFlow.test {
                assertThat(awaitItem().first { it.id == entry.id }.nickname).isEqualTo("Old Name")
                cancelAndIgnoreRemainingEvents()
            }
            cancelAndIgnoreRemainingEvents()
        }
    }
}
