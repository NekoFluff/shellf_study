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
import com.crazyfluff.shellfstudy.fakes.addFriendOrFail
import com.crazyfluff.shellfstudy.fakes.emptyResponse
import com.crazyfluff.shellfstudy.fakes.jsonResponse
import com.crazyfluff.shellfstudy.shared.data.ApiResult
import com.crazyfluff.shellfstudy.shared.data.FriendRepository
import com.crazyfluff.shellfstudy.shared.data.FriendStatsRepository
import com.crazyfluff.shellfstudy.shared.data.isAuthError
import com.crazyfluff.shellfstudy.shared.data.model.FriendEntry
import com.crazyfluff.shellfstudy.shared.database.friends.FriendStatsEntity
import com.crazyfluff.shellfstudy.shared.network.WaniKaniApi
import com.crazyfluff.shellfstudy.shared.network.createWaniKaniHttpClient
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlinx.serialization.json.Json
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Covers what a friend refresh does when only *some* of the requests it makes succeed.
 *
 * The regression this guards: every response used to be unwrapped with `as? ApiResult.Success` and a
 * failure fell through to `emptyList()`, so a friend whose `/assignments?started=true` call failed
 * (offline, 5xx, or a token revoked mid-refresh) was still cached — with zeroed learned/burned
 * counts, overwriting figures that were already on disk. A refresh that cannot answer every question
 * must leave the cached row alone and report the failure instead.
 */
class FriendStatsRefreshTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var friendRepository: FriendRepository
    private lateinit var friendStatsDao: FakeFriendStatsDao
    private lateinit var repository: FriendStatsRepository

    /** Requests whose path matches fail with [failingCode]; the rest answer normally. */
    private var shouldFail: (String) -> Boolean = { false }
    private var failingCode: Int = 500

    private val entry = FriendEntry(id = FRIEND_ID, nickname = "durtle_fan", encryptedToken = "enc:token")

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                if (shouldFail(path)) return emptyResponse(failingCode)
                return when {
                    path.startsWith("/user") -> jsonResponse(USER_JSON)
                    path.startsWith("/assignments") -> emptyCollection("assignment")
                    path.startsWith("/review_statistics") -> emptyCollection("review_statistic")
                    path.startsWith("/level_progressions") -> emptyCollection("level_progression")
                    else -> emptyResponse(404)
                }
            }
        }
        server.start()

        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(mainDispatcherRule.dispatcher + SupervisorJob()),
            produceFile = { tempFolder.newFile("test.preferences_pb") }
        )
        val json = Json { ignoreUnknownKeys = true }
        friendStatsDao = FakeFriendStatsDao()
        friendRepository = FriendRepository(dataStore, json, FakeTokenCipher())
        repository = FriendStatsRepository(
            friendRepository = friendRepository,
            friendStatsDao = friendStatsDao,
            json = json,
            selfAssignmentDao = FakeAssignmentDao(),
            selfReviewStatisticDao = FakeReviewStatisticDao(),
            selfLevelProgressionDao = FakeLevelProgressionDao(),
            defaultDispatcher = mainDispatcherRule.dispatcher,
            friendApiFactory = { token ->
                WaniKaniApi(
                    createWaniKaniHttpClient(tokenProvider = { token }),
                    baseUrl = server.url("/").toString()
                )
            }
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `a failed request reports the failure and leaves the cached stats untouched`() =
        runTest(mainDispatcherRule.dispatcher) {
            val cached = cachedStats(learnedAllTime = 42, burnedAllTime = 7)
            friendStatsDao.upsert(cached)
            shouldFail = { path -> path.contains("started=true") }

            val result = repository.refreshFriend(entry)

            assertThat(result).isInstanceOf(ApiResult.Error::class.java)
            assertThat(friendStatsDao.getById(FRIEND_ID)).isEqualTo(cached)
        }

    @Test
    fun `a failed request on an uncached friend writes no zeroed row`() =
        runTest(mainDispatcherRule.dispatcher) {
            shouldFail = { path -> path.contains("started=true") }

            val result = repository.refreshFriend(entry)

            assertThat(result).isInstanceOf(ApiResult.Error::class.java)
            assertThat(friendStatsDao.getById(FRIEND_ID)).isNull()
        }

    @Test
    fun `a fully successful refresh caches the fetched stats`() =
        runTest(mainDispatcherRule.dispatcher) {
            val result = repository.refreshFriend(entry)

            assertThat(result).isInstanceOf(ApiResult.Success::class.java)
            val stored = friendStatsDao.getById(FRIEND_ID)
            assertThat(stored?.username).isEqualTo("durtle_fan")
            assertThat(stored?.level).isEqualTo(12)
        }

    @Test
    fun `a revoked token surfaces as an auth error rather than a generic failure`() =
        runTest(mainDispatcherRule.dispatcher) {
            shouldFail = { path -> path.startsWith("/user") }
            failingCode = 401

            val result = repository.refreshFriend(entry)

            assertThat(result).isInstanceOf(ApiResult.Error::class.java)
            assertThat((result as ApiResult.Error).isAuthError).isTrue()
            assertThat(friendStatsDao.getById(FRIEND_ID)).isNull()
        }

    @Test
    fun `a fan-out reports the friends it could not refresh`() =
        runTest(mainDispatcherRule.dispatcher) {
            friendRepository.addFriendOrFail("durtle_fan", "token")
            shouldFail = { path -> path.contains("started=true") }

            val failures = repository.refreshAllIfStale(force = true)

            assertThat(failures.map { it.nickname }).containsExactly("durtle_fan")
        }

    @Test
    fun `a fan-out refreshes a friend whose cache has gone stale`() =
        runTest(mainDispatcherRule.dispatcher) {
            val friend = friendRepository.addFriendOrFail("durtle_fan", "token")
            friendStatsDao.upsert(cachedStats(learnedAllTime = 42, burnedAllTime = 7, friendId = friend.id))

            val failures = repository.refreshAllIfStale()

            assertThat(failures).isEmpty()
            val stored = friendStatsDao.getById(friend.id)
            assertThat(stored?.username).isEqualTo("durtle_fan")
            assertThat(stored?.fetchedAtMillis).isNotEqualTo(1L)
        }

    @Test
    fun `a fan-out leaves a friend inside the TTL alone unless it is forced`() =
        runTest(mainDispatcherRule.dispatcher) {
            val friend = friendRepository.addFriendOrFail("durtle_fan", "token")
            friendStatsDao.upsert(
                cachedStats(
                    learnedAllTime = 42,
                    burnedAllTime = 7,
                    friendId = friend.id,
                    fetchedAtMillis = Clock.System.now().toEpochMilliseconds()
                )
            )

            val skipped = repository.refreshAllIfStale()
            assertThat(skipped).isEmpty()
            assertThat(server.requestCount).isEqualTo(0)

            // force bypasses the TTL — that is what makes pull-to-refresh mean "now", not "if stale".
            val forced = repository.refreshAllIfStale(force = true)
            assertThat(forced).isEmpty()
            assertThat(server.requestCount).isGreaterThan(0)
        }

    private fun cachedStats(
        learnedAllTime: Int,
        burnedAllTime: Int,
        friendId: String = FRIEND_ID,
        fetchedAtMillis: Long = 1L
    ) = FriendStatsEntity(
        friendId = friendId,
        username = "stale",
        level = 3,
        reviewAccuracy = 0f,
        avgDaysPerLevel = null,
        daysSinceStart = null,
        levelTimelineJson = "[]",
        fetchedAtMillis = fetchedAtMillis,
        learnedAllTime = learnedAllTime,
        burnedAllTime = burnedAllTime
    )

    private fun emptyCollection(objectType: String, code: Int = 200): MockResponse =
        jsonResponse("""{"object":"$objectType","url":"https://api.wanikani.com/v2/$objectType","data":[]}""", code)

    private companion object {
        const val FRIEND_ID = "friend-1"

        val USER_JSON = """
            {
              "object": "user",
              "url": "https://api.wanikani.com/v2/user",
              "data_updated_at": "2026-01-01T00:00:00.000000Z",
              "data": {
                "id": "abc-123",
                "username": "durtle_fan",
                "level": 12,
                "profile_url": "https://www.wanikani.com/users/durtle_fan",
                "started_at": "2020-01-01T00:00:00.000000Z"
              }
            }
        """.trimIndent()
    }
}
