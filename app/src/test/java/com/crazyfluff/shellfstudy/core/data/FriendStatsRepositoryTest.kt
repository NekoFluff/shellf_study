package com.crazyfluff.shellfstudy.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.crazyfluff.shellfstudy.MainDispatcherRule
import com.crazyfluff.shellfstudy.fakes.FakeAssignmentDao
import com.crazyfluff.shellfstudy.fakes.FakeFriendStatsDao
import com.crazyfluff.shellfstudy.fakes.FakeLevelProgressionDao
import com.crazyfluff.shellfstudy.fakes.FakeReviewStatisticDao
import com.crazyfluff.shellfstudy.fakes.FakeTokenCipher
import com.crazyfluff.shellfstudy.shared.data.FriendRepository
import com.crazyfluff.shellfstudy.shared.data.FriendStatsRepository
import com.crazyfluff.shellfstudy.shared.data.model.FriendStats
import com.crazyfluff.shellfstudy.shared.data.model.Leaderboard
import com.crazyfluff.shellfstudy.shared.data.model.LeaderboardMetric
import com.crazyfluff.shellfstudy.shared.data.model.LeaderboardWindow
import com.crazyfluff.shellfstudy.shared.database.friends.FriendStatsEntity
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
 * Covers the roster index every leaderboard surface derives a user's color from. Its whole point is
 * that it is *not* the entry's position in [Leaderboard.entries]: that position is rank, which
 * changes with the metric/window and shrinks when a friend has no cached stats. If these tests fail,
 * users start changing color between the leaderboard rows, the charts, and the friends list.
 */
class FriendStatsRepositoryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var friendRepository: FriendRepository
    private lateinit var friendStatsDao: FakeFriendStatsDao
    private lateinit var repository: FriendStatsRepository

    @Before
    fun setUp() {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(mainDispatcherRule.dispatcher + SupervisorJob()),
            produceFile = { tempFolder.newFile("test.preferences_pb") }
        )
        val json = Json { ignoreUnknownKeys = true }
        friendRepository = FriendRepository(dataStore, json, FakeTokenCipher())
        friendStatsDao = FakeFriendStatsDao()
        repository = FriendStatsRepository(
            friendRepository = friendRepository,
            friendStatsDao = friendStatsDao,
            json = json,
            selfAssignmentDao = FakeAssignmentDao(),
            selfReviewStatisticDao = FakeReviewStatisticDao(),
            selfLevelProgressionDao = FakeLevelProgressionDao(),
            defaultDispatcher = mainDispatcherRule.dispatcher
        )
    }

    @Test
    fun `roster index is self at 0 then friends in the order they were added`() =
        runTest(mainDispatcherRule.dispatcher) {
            val a = friendRepository.addFriend("A", "token-a")
            val b = friendRepository.addFriend("B", "token-b")
            val c = friendRepository.addFriend("C", "token-c")
            listOf(a, b, c).forEach { friendStatsDao.upsert(entity(it.id)) }

            val byNickname = repository.observeLeaderboard().first()!!.rosterIndexByNickname()

            assertThat(byNickname).isEqualTo(mapOf("You" to 0, "A" to 1, "B" to 2, "C" to 3))
        }

    @Test
    fun `a friend with no cached stats does not shift the friends added after them`() =
        runTest(mainDispatcherRule.dispatcher) {
            val a = friendRepository.addFriend("A", "token-a")
            friendRepository.addFriend("B", "token-b")
            val c = friendRepository.addFriend("C", "token-c")
            // B has never been fetched, so it is absent from the leaderboard entirely — but C must
            // keep the index it would otherwise have had, or C's color would depend on B's cache.
            listOf(a, c).forEach { friendStatsDao.upsert(entity(it.id)) }

            val byNickname = repository.observeLeaderboard().first()!!.rosterIndexByNickname()

            assertThat(byNickname).isEqualTo(mapOf("You" to 0, "A" to 1, "C" to 3))
        }

    @Test
    fun `roster index survives re-sorting by metric and window`() =
        runTest(mainDispatcherRule.dispatcher) {
            val a = friendRepository.addFriend("A", "token-a")
            val b = friendRepository.addFriend("B", "token-b")
            // B leads on lessons, A leads on level, so the two leaderboards rank them oppositely.
            friendStatsDao.upsert(entity(a.id, level = 30, learnedWeek = 1))
            friendStatsDao.upsert(entity(b.id, level = 5, learnedWeek = 99))

            val byLessons = repository
                .observeLeaderboard(LeaderboardMetric.LEARNED, LeaderboardWindow.WEEK).first()!!
            val byLevel = repository
                .observeLeaderboard(LeaderboardMetric.LEVEL, LeaderboardWindow.WEEK).first()!!

            assertThat(byLessons.entries.map(FriendStats::nickname))
                .isNotEqualTo(byLevel.entries.map(FriendStats::nickname))
            assertThat(byLessons.rosterIndexByNickname())
                .isEqualTo(byLevel.rosterIndexByNickname())
        }

    private fun entity(
        friendId: String,
        level: Int = 1,
        learnedWeek: Int = 0
    ) = FriendStatsEntity(
        friendId = friendId,
        username = "user-$friendId",
        level = level,
        reviewAccuracy = 1f,
        avgDaysPerLevel = -1f,
        daysSinceStart = -1,
        levelTimelineJson = "[]",
        fetchedAtMillis = 0L,
        learnedWeek = learnedWeek
    )
}

private fun Leaderboard.rosterIndexByNickname(): Map<String, Int> =
    entries.associate { it.nickname to it.rosterIndex }
