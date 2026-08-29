package com.crazyfluff.shellfstudy.shared.data

import com.crazyfluff.shellfstudy.shared.data.model.ActivityBuckets
import com.crazyfluff.shellfstudy.shared.data.model.ActivityStats
import com.crazyfluff.shellfstudy.shared.data.model.FriendEntry
import com.crazyfluff.shellfstudy.shared.data.model.FriendStats
import com.crazyfluff.shellfstudy.shared.data.model.Leaderboard
import com.crazyfluff.shellfstudy.shared.data.model.LeaderboardMetric
import com.crazyfluff.shellfstudy.shared.data.model.LeaderboardWindow
import com.crazyfluff.shellfstudy.shared.data.model.LevelTimelinePoint
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import com.crazyfluff.shellfstudy.shared.database.AssignmentDao
import com.crazyfluff.shellfstudy.shared.database.LevelProgressionDao
import com.crazyfluff.shellfstudy.shared.database.LevelProgressionEntity
import com.crazyfluff.shellfstudy.shared.database.ReviewStatisticDao
import com.crazyfluff.shellfstudy.shared.database.ReviewStatisticEntity
import com.crazyfluff.shellfstudy.shared.database.friends.FriendStatsDao
import com.crazyfluff.shellfstudy.shared.database.friends.FriendStatsEntity
import com.crazyfluff.shellfstudy.shared.network.WaniKaniApi
import com.crazyfluff.shellfstudy.shared.network.collectAllPages
import com.crazyfluff.shellfstudy.shared.network.createFriendWaniKaniApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val FRIEND_STATS_TTL = 30.minutes
private val DAY = 1.days

@Serializable
internal data class TimelinePointJson(val daysSinceStart: Int, val level: Int)

internal fun buildTimeline(sortedProgressions: List<Pair<Int, String>>): List<TimelinePointJson> {
    if (sortedProgressions.isEmpty()) return emptyList()
    val startMillis = parseIsoToMillis(sortedProgressions.first().second) ?: return emptyList()
    return sortedProgressions.mapNotNull { (level, ts) ->
        val ms = parseIsoToMillis(ts) ?: return@mapNotNull null
        TimelinePointJson(daysSinceStart = ((ms - startMillis).milliseconds / DAY).toInt(), level = level)
    }
}

private fun parseIsoToMillis(iso: String): Long? =
    runCatching { Instant.parse(iso).toEpochMilliseconds() }.getOrNull()

internal data class WindowedCounts(val today: Int, val week: Int, val month: Int, val year: Int, val allTime: Int)

internal fun computeActivityBuckets(
    isoTimestamps: List<String?>,
    nowMillis: Long,
    tz: TimeZone = TimeZone.currentSystemDefault()
): ActivityBuckets {
    val nowDt = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(tz)
    val nowTotalMonths = nowDt.year * 12 + (nowDt.month.number - 1)
    val nowLocalDays = nowDt.date.toEpochDays()

    val weekDays = IntArray(7)
    val monthDays = IntArray(30)
    val yearMonths = IntArray(12)

    // Parse all timestamps up front so we can find the earliest month for allTimeMonths sizing
    val parsed = isoTimestamps.mapNotNull { ts ->
        ts?.let { runCatching { Instant.parse(it) }.getOrNull() }
    }

    val earliestTotalMonths = parsed.minOfOrNull { inst ->
        val dt = inst.toLocalDateTime(tz)
        dt.year * 12 + (dt.month.number - 1)
    } ?: nowTotalMonths
    val allTimeLen = (nowTotalMonths - earliestTotalMonths + 1).coerceAtLeast(1)
    val allTimeMonths = IntArray(allTimeLen)

    for (inst in parsed) {
        val tsDt = inst.toLocalDateTime(tz)
        val daysAgo = (nowLocalDays - tsDt.date.toEpochDays()).toInt()
        if (daysAgo in 0..6) weekDays[6 - daysAgo]++
        if (daysAgo in 0..29) monthDays[29 - daysAgo]++
        val tsTotalMonths = tsDt.year * 12 + (tsDt.month.number - 1)
        val monthsAgo = nowTotalMonths - tsTotalMonths
        if (monthsAgo in 0..11) yearMonths[11 - monthsAgo]++
        val allTimeIdx = tsTotalMonths - earliestTotalMonths
        if (allTimeIdx in 0 until allTimeLen) allTimeMonths[allTimeIdx]++
    }

    return ActivityBuckets(weekDays.toList(), monthDays.toList(), yearMonths.toList(), allTimeMonths.toList())
}

/**
 * Derived directly from [ActivityBuckets] — the same buckets rendered as the graph's bars — so a
 * window total is *structurally* guaranteed to equal the sum of the matching bars, rather than
 * relying on two separate implementations of the same calendar-day math staying in sync by
 * coincidence. `today` is the bucket for daysAgo == 0, i.e. the last entry of `weekDays`.
 */
internal fun computeWindowedCounts(buckets: ActivityBuckets): WindowedCounts = WindowedCounts(
    today = buckets.weekDays.last(),
    week = buckets.weekDays.sum(),
    month = buckets.monthDays.sum(),
    year = buckets.yearMonths.sum(),
    allTime = buckets.allTimeMonths.sum()
)

internal fun computeAvgDaysPerLevel(sortedProgressions: List<Pair<Int, String>>): Float? {
    if (sortedProgressions.size < 2) return null
    val millis = sortedProgressions.mapNotNull { (_, ts) -> parseIsoToMillis(ts) }
    if (millis.size < 2) return null
    val intervals = millis.zipWithNext().map { (a, b) -> ((b - a).milliseconds / DAY).toFloat() }
    return intervals.average().toFloat()
}

internal data class StatsCore(
    val reviewAccuracy: Float,
    val avgDaysPerLevel: Float?,
    val daysSinceStart: Int?,
    val timeline: List<TimelinePointJson>,
    val learned: ActivityStats,
    val burned: ActivityStats,
    val learnedBuckets: ActivityBuckets,
    val burnedBuckets: ActivityBuckets
)

/**
 * Shared arithmetic behind both [FriendStatsRepository.fetchFriendStats] (network path, API item
 * lists) and [FriendStatsRepository.buildSelfStats] (local-DB path, Room entities) — each caller
 * extracts its own input-shape-specific raw timestamps first, then converges here.
 *
 * An assignment only counts as learned/burned once it has a real, parseable started_at/burned_at.
 * [computeWindowedCounts] is derived from the same [ActivityBuckets] rendered as the graph, so the
 * table's totals and the graph's bars can never disagree.
 */
internal fun buildStatsCore(
    burnedTimestamps: List<String?>,
    learnedTimestamps: List<String?>,
    totalCorrect: Float,
    totalAttempts: Float,
    sortedProgressions: List<Pair<Int, String>>,
    nowMillis: Long
): StatsCore {
    val learnedBuckets = computeActivityBuckets(learnedTimestamps, nowMillis)
    val burnedBuckets = computeActivityBuckets(burnedTimestamps, nowMillis)
    val learnedCounts = computeWindowedCounts(learnedBuckets)
    val burnedCounts = computeWindowedCounts(burnedBuckets)

    val accuracy = if (totalAttempts > 0) totalCorrect / totalAttempts else -1f

    val avgDaysPerLevel = computeAvgDaysPerLevel(sortedProgressions)
    val daysSinceStart = sortedProgressions.firstOrNull()?.let { (_, unlockedAt) ->
        val startMillis = parseIsoToMillis(unlockedAt)
        if (startMillis != null) ((nowMillis - startMillis).milliseconds / DAY).toInt() else null
    }
    val timeline = buildTimeline(sortedProgressions)

    return StatsCore(
        reviewAccuracy = accuracy,
        avgDaysPerLevel = avgDaysPerLevel,
        daysSinceStart = daysSinceStart,
        timeline = timeline,
        learned = ActivityStats(
            today = learnedCounts.today,
            week = learnedCounts.week,
            month = learnedCounts.month,
            year = learnedCounts.year,
            allTime = learnedCounts.allTime
        ),
        burned = ActivityStats(
            today = burnedCounts.today,
            week = burnedCounts.week,
            month = burnedCounts.month,
            year = burnedCounts.year,
            allTime = burnedCounts.allTime
        ),
        learnedBuckets = learnedBuckets,
        burnedBuckets = burnedBuckets
    )
}

class FriendStatsRepository(
    private val friendRepository: FriendRepository,
    private val friendStatsDao: FriendStatsDao,
    private val json: Json,
    private val selfAssignmentDao: AssignmentDao,
    private val selfReviewStatisticDao: ReviewStatisticDao,
    private val selfLevelProgressionDao: LevelProgressionDao,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    // Pre-built self-stats flow; shared across all observeLeaderboard subscriptions so Room
    // doesn't open duplicate queries when metric/window changes (only the re-sort changes).
    //
    // The four DAO flows only re-emit on a DB write, so combining with dailyRolloverTicks is what
    // actually rolls the "learned/burned today" figures over at local midnight — otherwise, on a
    // night with no new assignment/review/progression write, buildSelfStats's `nowMillis` would stay
    // frozen at whatever it was last computed until the next unrelated write (or an app restart).
    private val selfStatsFlow: Flow<FriendStats> = combine(
        selfAssignmentDao.observeAllBurnedTimestamps(),
        selfAssignmentDao.observeAllStartedTimestamps(),
        selfReviewStatisticDao.observeAll(),
        selfLevelProgressionDao.observeAll(),
        dailyRolloverTicks(TimeZone.currentSystemDefault())
    ) { burnedTs, startedTs, statistics, progressions, _ ->
        buildSelfStats(burnedTs, startedTs, statistics, progressions)
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
            val friendEntries = friends.mapNotNull { entry ->
                statsByFriendId[entry.id]?.toFriendStats(nickname = entry.nickname)
            }

            val all = listOf(selfStats) + friendEntries
            Leaderboard(entries = all, metric = metric, window = window, selfRank = null)
                .sorted(by = metric, window = window)
        }.flowOn(defaultDispatcher)

    suspend fun refreshAllIfStale(force: Boolean = false) {
        val friends = friendRepository.friendsFlow.first()
        coroutineScope {
            friends.map { entry ->
                async {
                    val cached = friendStatsDao.getById(entry.id)
                    val nowMillis = Clock.System.now().toEpochMilliseconds()
                    val isStale = cached == null ||
                        (nowMillis - cached.fetchedAtMillis) > FRIEND_STATS_TTL.inWholeMilliseconds
                    if (force || isStale) refreshFriend(entry)
                }
            }.awaitAll()
        }
    }

    /**
     * @return false if the friend's token failed to decrypt, the API call failed, or the response
     * couldn't be parsed into stats — callers use this to distinguish "nothing new to fetch" from
     * a real failure. Never throws: a bad token (e.g. a Keystore entry invalidated after a device
     * unlock change) must not cancel sibling refreshes running in the same `coroutineScope`.
     */
    suspend fun refreshFriend(entry: FriendEntry): Boolean {
        val entity = runCatching {
            val token = friendRepository.decryptToken(entry)
            val api = createFriendWaniKaniApi(token, json)
            fetchFriendStats(entry.id, api)
        }.getOrNull() ?: return false
        friendStatsDao.upsert(entity)
        return true
    }

    suspend fun removeFriendCache(id: String) {
        friendStatsDao.deleteById(id)
    }

    private suspend fun fetchFriendStats(friendId: String, api: WaniKaniApi): FriendStatsEntity? {
        val userResult = safeApiCall { api.getUser() }
        val userData = (userResult as? ApiResult.Success)?.data?.data ?: return null

        val nowMillis = Clock.System.now().toEpochMilliseconds()

        // All burned assignments → all-time + windowed burned counts
        val burnedResult = safeApiCall {
            collectAllPages(
                firstPage = { api.getAssignments(burned = true) },
                nextPage = { url -> api.getAssignmentsPage(url) }
            )
        }
        val burnedItems = (burnedResult as? ApiResult.Success)?.data ?: emptyList()
        val burnedTimestamps = burnedItems.map { it.data.burnedAt }

        // All started assignments → all-time + windowed learned counts
        val learnedResult = safeApiCall {
            collectAllPages(
                firstPage = { api.getAssignments(started = true) },
                nextPage = { url -> api.getAssignmentsPage(url) }
            )
        }
        val learnedItems = (learnedResult as? ApiResult.Success)?.data ?: emptyList()
        val learnedTimestamps = learnedItems.map { it.data.startedAt }

        // Review statistics → accuracy + all-time totals
        val statsResult = safeApiCall {
            collectAllPages(
                firstPage = { api.getReviewStatistics() },
                nextPage = { url -> api.getReviewStatisticsPage(url) }
            )
        }
        val statsItems = (statsResult as? ApiResult.Success)?.data ?: emptyList()
        val totalCorrect = statsItems.sumOf { it.data.meaningCorrect + it.data.readingCorrect }.toFloat()
        val totalAttempts = statsItems.sumOf {
            it.data.meaningCorrect + it.data.meaningIncorrect +
                it.data.readingCorrect + it.data.readingIncorrect
        }

        // Level progressions → timeline + avg speed
        val progressionsResult = safeApiCall { api.getLevelProgressions() }
        val sortedProgressions = (progressionsResult as? ApiResult.Success)?.data?.data
            ?.mapNotNull { item -> item.data.unlockedAt?.let { item.data.level to it } }
            ?.sortedBy { it.second }
            ?: emptyList()

        val core = buildStatsCore(
            burnedTimestamps = burnedTimestamps,
            learnedTimestamps = learnedTimestamps,
            totalCorrect = totalCorrect,
            totalAttempts = totalAttempts.toFloat(),
            sortedProgressions = sortedProgressions,
            nowMillis = nowMillis
        )

        val timelineJson = json.encodeToString(ListSerializer(TimelinePointJson.serializer()), core.timeline)

        return FriendStatsEntity(
            friendId = friendId,
            username = userData.username,
            level = userData.level,
            reviewAccuracy = core.reviewAccuracy,
            avgDaysPerLevel = core.avgDaysPerLevel ?: -1f,
            daysSinceStart = core.daysSinceStart ?: -1,
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
            totalReviews = totalAttempts,
            learnedBucketsJson = json.encodeToString(ActivityBuckets.serializer(), core.learnedBuckets),
            burnedBucketsJson = json.encodeToString(ActivityBuckets.serializer(), core.burnedBuckets)
        )
    }

    private fun buildSelfStats(
        burnedTimestamps: List<String>,
        startedTimestamps: List<String>,
        statistics: List<ReviewStatisticEntity>,
        progressions: List<LevelProgressionEntity>
    ): FriendStats {
        val nowMillis = Clock.System.now().toEpochMilliseconds()

        val totalCorrect = statistics.sumOf { it.meaningCorrect + it.readingCorrect }.toFloat()
        val totalAttempts = statistics.sumOf {
            it.meaningCorrect + it.meaningIncorrect + it.readingCorrect + it.readingIncorrect
        }

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
            level = progressions.maxOfOrNull { it.level } ?: 0,
            reviewAccuracy = core.reviewAccuracy,
            avgDaysPerLevel = core.avgDaysPerLevel,
            daysSinceStart = core.daysSinceStart,
            levelTimeline = core.timeline.map { LevelTimelinePoint(it.daysSinceStart, it.level) },
            isCurrentUser = true,
            learned = core.learned,
            burned = core.burned,
            learnedBuckets = core.learnedBuckets,
            burnedBuckets = core.burnedBuckets
        )
    }

    private fun FriendStatsEntity.toFriendStats(nickname: String): FriendStats {
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
            avgDaysPerLevel = if (avgDaysPerLevel < 0f) null else avgDaysPerLevel,
            daysSinceStart = if (daysSinceStart < 0) null else daysSinceStart,
            levelTimeline = timeline,
            isCurrentUser = false,
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
