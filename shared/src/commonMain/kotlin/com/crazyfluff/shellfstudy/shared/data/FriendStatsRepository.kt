package com.crazyfluff.shellfstudy.shared.data

import com.crazyfluff.shellfstudy.shared.data.model.ActivityBuckets
import com.crazyfluff.shellfstudy.shared.data.model.ActivityStats
import com.crazyfluff.shellfstudy.shared.data.model.FriendEntry
import com.crazyfluff.shellfstudy.shared.data.model.FriendStats
import com.crazyfluff.shellfstudy.shared.data.model.Leaderboard
import com.crazyfluff.shellfstudy.shared.data.model.LeaderboardMetric
import com.crazyfluff.shellfstudy.shared.data.model.LeaderboardWindow
import com.crazyfluff.shellfstudy.shared.data.model.LevelTimelinePoint
import com.crazyfluff.shellfstudy.shared.data.model.SELF_ROSTER_INDEX
import com.crazyfluff.shellfstudy.shared.data.model.friendRosterIndex
import com.crazyfluff.shellfstudy.shared.database.AssignmentDao
import com.crazyfluff.shellfstudy.shared.database.LevelProgressionDao
import com.crazyfluff.shellfstudy.shared.database.LevelProgressionEntity
import com.crazyfluff.shellfstudy.shared.database.ReviewStatisticDao
import com.crazyfluff.shellfstudy.shared.database.ReviewAccuracyTotals
import com.crazyfluff.shellfstudy.shared.database.friends.FriendStatsDao
import com.crazyfluff.shellfstudy.shared.database.friends.FriendStatsEntity
import com.crazyfluff.shellfstudy.shared.network.WaniKaniApi
import com.crazyfluff.shellfstudy.shared.network.collectAllPages
import com.crazyfluff.shellfstudy.shared.network.createFriendWaniKaniApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

private val FRIEND_STATS_TTL = 30.minutes

/**
 * A friend a [FriendStatsRepository.refreshAllIfStale] fan-out could not update. Carries the nickname
 * so a caller can name who is missing rather than only how many, and the original [ApiResult.Error]
 * so [isAuthError] still identifies a revoked token.
 */
data class FriendStatsRefreshFailure(val nickname: String, val error: ApiResult.Error)

class FriendStatsRepository(
    private val friendRepository: FriendRepository,
    private val friendStatsDao: FriendStatsDao,
    private val json: Json,
    private val selfAssignmentDao: AssignmentDao,
    private val selfReviewStatisticDao: ReviewStatisticDao,
    private val selfLevelProgressionDao: LevelProgressionDao,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /**
     * Builds the client for one friend's token. Injectable so a test can aim a friend's requests at
     * a MockEngine — the refresh path otherwise builds its own client, which left the partial-fetch
     * behaviour in [fetchFriendStats] unreachable from a test.
     */
    private val friendApiFactory: (String) -> WaniKaniApi = { token -> createFriendWaniKaniApi(token, json) }
) {
    // The self-stats figures, recomputed whenever one of their sources changes.
    //
    // Not `shareIn`'d, despite what this comment claimed for a while: the only collectors are the
    // dashboard's leaderboard card and the leaderboard screen, which are never on screen at the same
    // time, so sharing would buy one subscription instead of one — at the cost of a repository-owned
    // CoroutineScope that would outlive every screen and have to be cancelled somewhere.
    //
    // The four DAO flows only re-emit on a DB write, so combining with dailyRolloverTicks is what
    // actually rolls the "learned/burned today" figures over at local midnight — otherwise, on a
    // night with no new assignment/review/progression write, buildSelfStats's `nowMillis` would stay
    // frozen at whatever it was last computed until the next unrelated write (or an app restart).
    private val selfStatsFlow: Flow<FriendStats> = combine(
        selfAssignmentDao.observeAllBurnedTimestamps(),
        selfAssignmentDao.observeAllStartedTimestamps(),
        selfReviewStatisticDao.observeAccuracyTotals(),
        selfLevelProgressionDao.observeAll(),
        dailyRolloverTicks()
    ) { burnedTs, startedTs, accuracyTotals, progressions, _ ->
        buildSelfStats(burnedTs, startedTs, accuracyTotals, progressions)
    }.flowOn(defaultDispatcher)

    fun observeLeaderboard(
        metric: LeaderboardMetric = LeaderboardMetric.LEARNED,
        window: LeaderboardWindow = LeaderboardWindow.WEEK
    ): Flow<Leaderboard?> =
        combine(
            friendRepository.friendsFlow,
            friendStatsDao.observeAll(),
            selfStatsFlow
        ) { friends, cachedStats, selfStats ->
            if (friends.isEmpty()) return@combine null

            val statsByFriendId = cachedStats.associateBy { it.friendId }
            // Indexed against the unfiltered list, then filtered: a friend whose stats aren't
            // cached yet must not shift the roster index (and so the color) of the friends after
            // them. Ids of friends that were removed leave a gap, which only wastes a palette slot.
            val friendEntries = friends.mapIndexedNotNull { index, entry ->
                statsByFriendId[entry.id]?.toFriendStats(
                    nickname = entry.nickname,
                    rosterIndex = friendRosterIndex(index)
                )
            }

            val all = listOf(selfStats) + friendEntries
            Leaderboard(entries = all, metric = metric, window = window)
                .sorted(by = metric, window = window)
        }.flowOn(defaultDispatcher)

    /**
     * Refreshes every friend whose cached figures have fallen outside [FRIEND_STATS_TTL], or all of
     * them when [force] is set.
     *
     * Returns the friends that could *not* be refreshed rather than returning [Unit]: a fan-out that
     * discards [refreshFriend]'s per-friend [ApiResult] cannot distinguish "everyone is current" from
     * "everyone failed", which is how the dashboard's background refresh came to swallow revoked
     * tokens silently while the leaderboard's identical-looking refresh reported them. An empty list
     * means every friend that needed refreshing now is.
     */
    suspend fun refreshAllIfStale(force: Boolean = false): List<FriendStatsRefreshFailure> {
        val friends = friendRepository.friendsFlow.first()
        // One clock reading for the whole fan-out. Taking it per friend made the staleness decision
        // depend on how long the friends ahead of them took to refresh, so a friend could be judged
        // stale against a "now" that had already drifted past the TTL.
        val nowMillis = Clock.System.now().toEpochMilliseconds()
        return coroutineScope {
            friends.map { entry ->
                async {
                    val cached = friendStatsDao.getById(entry.id)
                    val isStale = cached == null ||
                        (nowMillis - cached.fetchedAtMillis) > FRIEND_STATS_TTL.inWholeMilliseconds
                    if (!force && !isStale) return@async null
                    (refreshFriend(entry) as? ApiResult.Error)
                        ?.let { FriendStatsRefreshFailure(entry.nickname, it) }
                }
            }.awaitAll().filterNotNull()
        }
    }

    /**
     * @return [ApiResult.Error] if the friend's token failed to decrypt, any of the API calls
     * failed, or the cache write itself failed — callers use this to distinguish "nothing new to
     * fetch" from a real failure. The underlying failure is passed through rather than flattened
     * into one generic message, so [isAuthError] still identifies a revoked token. Never throws
     * (cancellation excepted): a bad token (e.g. a Keystore entry invalidated after a device unlock
     * change) must not cancel sibling refreshes running in the same `coroutineScope`.
     *
     * The cache is written only when every fetch succeeded. A partial failure must not replace the
     * friend's cached figures with zeros for whichever endpoints did answer — that is exactly how an
     * offline refresh used to erase a friend's real stats.
     */
    suspend fun refreshFriend(entry: FriendEntry): ApiResult<Unit> {
        // A fresh HttpClient (own connection pool/dispatcher threads) is built per call, per friend
        // — closed in `finally` so a leaderboard with several friends doesn't leak one live engine
        // per friend on every refreshAllIfStale fan-out.
        var api: WaniKaniApi? = null
        return try {
            api = friendApiFactory(friendRepository.decryptToken(entry))
            when (val result = fetchFriendStats(entry.id, api)) {
                is ApiResult.Error -> result
                is ApiResult.Success -> {
                    friendStatsDao.upsert(result.data)
                    ApiResult.Success(Unit)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ApiResult.Error("Couldn't refresh ${entry.nickname}'s stats.", e)
        } finally {
            api?.close()
        }
    }

    suspend fun removeFriendCache(id: String) {
        friendStatsDao.deleteById(id)
    }

    /**
     * Fetches every endpoint a friend's stats are assembled from.
     *
     * Fails as a whole rather than degrading per endpoint: an empty assignment or statistics list
     * means "this friend has none", which is indistinguishable from "that call failed", so treating
     * a failure as an empty list silently cached a zeroed friend over real figures.
     */
    private suspend fun fetchFriendStats(friendId: String, api: WaniKaniApi): ApiResult<FriendStatsEntity> = safeApiCall {
        val userData = api.getUser().data
        val nowMillis = Clock.System.now().toEpochMilliseconds()

        // Every started assignment → all-time + windowed learned counts, and, from the ones among
        // them that are burned, the burned counts too. One walk rather than a second `burned=true`
        // one: a burned assignment is always a started one, and this is the largest collection a
        // friend refresh pages through.
        val startedItems = collectAllPages(
            firstPage = { api.getAssignments(started = true) },
            nextPage = { url -> api.getAssignmentsPage(url) }
        )
        val learnedTimestamps = startedItems.map { it.data.startedAt }
        val burnedTimestamps = startedItems.mapNotNull { it.data.burnedAt }

        // Review statistics → accuracy + all-time totals
        val statsItems = collectAllPages(
            firstPage = { api.getReviewStatistics() },
            nextPage = { url -> api.getReviewStatisticsPage(url) }
        )
        val totalCorrect = statsItems.sumOf { it.data.meaningCorrect + it.data.readingCorrect }.toFloat()
        val totalAttempts = statsItems.sumOf {
            it.data.meaningCorrect + it.data.meaningIncorrect +
                it.data.readingCorrect + it.data.readingIncorrect
        }

        // Level progressions → timeline + avg speed
        val sortedProgressions = api.getLevelProgressions().data
            .mapNotNull { item -> item.data.unlockedAt?.let { item.data.level to it } }
            .sortedBy { it.second }

        val core = buildStatsCore(
            burnedTimestamps = burnedTimestamps,
            learnedTimestamps = learnedTimestamps,
            totalCorrect = totalCorrect,
            totalAttempts = totalAttempts.toFloat(),
            sortedProgressions = sortedProgressions,
            nowMillis = nowMillis
        )

        val timelineJson = json.encodeToString(ListSerializer(TimelinePointJson.serializer()), core.timeline)

        FriendStatsEntity(
            friendId = friendId,
            username = userData.username,
            level = userData.level,
            reviewAccuracy = core.reviewAccuracy,
            avgDaysPerLevel = core.avgDaysPerLevel,
            daysSinceStart = core.daysSinceStart,
            levelTimelineJson = timelineJson,
            fetchedAtMillis = nowMillis,
            learnedToday = core.learned.today,
            learnedWeek = core.learned.week,
            learnedMonth = core.learned.month,
            learnedYear = core.learned.year,
            learnedAllTime = core.learned.allTime,
            burnedToday = core.burned.today,
            burnedWeek = core.burned.week,
            burnedMonth = core.burned.month,
            burnedYear = core.burned.year,
            burnedAllTime = core.burned.allTime,
            learnedBucketsJson = json.encodeToString(ActivityBuckets.serializer(), core.learnedBuckets),
            burnedBucketsJson = json.encodeToString(ActivityBuckets.serializer(), core.burnedBuckets)
        )
    }

    private fun buildSelfStats(
        burnedTimestamps: List<String>,
        startedTimestamps: List<String>,
        accuracyTotals: ReviewAccuracyTotals,
        progressions: List<LevelProgressionEntity>
    ): FriendStats {
        val nowMillis = Clock.System.now().toEpochMilliseconds()

        // Summed by SQLite — see ReviewStatisticDao.observeAccuracyTotals. Null means the table is
        // empty, which is the "unknown accuracy" case rather than a 0% one.
        val totalCorrect = (accuracyTotals.correct ?: 0L).toFloat()
        val totalAttempts = (accuracyTotals.attempts ?: 0L).toFloat()

        val sortedProgressions = progressions
            .mapNotNull { p -> p.unlockedAt?.let { p.level to it } }
            .sortedBy { it.second }

        val core = buildStatsCore(
            burnedTimestamps = burnedTimestamps,
            learnedTimestamps = startedTimestamps,
            totalCorrect = totalCorrect,
            totalAttempts = totalAttempts.toFloat(),
            sortedProgressions = sortedProgressions,
            nowMillis = nowMillis
        )

        return FriendStats(
            friendEntryId = "",
            nickname = "You",
            username = "",
            // The level being studied, as the dashboard reports it — the highest level a reset has
            // not abandoned — falling back to the highest level for an account that has passed them all.
            level = progressions.currentLevelProgression()?.level ?: progressions.maxOfOrNull { it.level } ?: 0,
            reviewAccuracy = core.reviewAccuracy,
            avgDaysPerLevel = core.avgDaysPerLevel,
            daysSinceStart = core.daysSinceStart,
            levelTimeline = core.timeline.map { LevelTimelinePoint(it.daysSinceStart, it.level) },
            isCurrentUser = true,
            rosterIndex = SELF_ROSTER_INDEX,
            learned = core.learned,
            burned = core.burned,
            learnedBuckets = core.learnedBuckets,
            burnedBuckets = core.burnedBuckets
        )
    }

    private fun FriendStatsEntity.toFriendStats(nickname: String, rosterIndex: Int): FriendStats {
        val timeline = runCatching {
            json.decodeFromString(ListSerializer(TimelinePointJson.serializer()), levelTimelineJson)
                .map { LevelTimelinePoint(it.daysSinceStart, it.level) }
        }.getOrDefault(emptyList())
        val learnedBuckets = runCatching {
            json.decodeFromString(ActivityBuckets.serializer(), learnedBucketsJson)
        }.getOrDefault(ActivityBuckets())
        val burnedBuckets = runCatching {
            json.decodeFromString(ActivityBuckets.serializer(), burnedBucketsJson)
        }.getOrDefault(ActivityBuckets())
        return FriendStats(
            friendEntryId = friendId,
            nickname = nickname,
            username = username,
            level = level,
            reviewAccuracy = reviewAccuracy,
            avgDaysPerLevel = avgDaysPerLevel,
            daysSinceStart = daysSinceStart,
            levelTimeline = timeline,
            isCurrentUser = false,
            rosterIndex = rosterIndex,
            learned = ActivityStats(
                today = learnedToday,
                week = learnedWeek,
                month = learnedMonth,
                year = learnedYear,
                allTime = learnedAllTime
            ),
            burned = ActivityStats(
                today = burnedToday,
                week = burnedWeek,
                month = burnedMonth,
                year = burnedYear,
                allTime = burnedAllTime
            ),
            learnedBuckets = learnedBuckets,
            burnedBuckets = burnedBuckets
        )
    }

}
