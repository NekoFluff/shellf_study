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
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val FRIEND_STATS_TTL = 30.minutes
private const val DAY_MS = 86_400_000L
private const val WEEK_MS = 7 * DAY_MS
private const val MONTH_MS = 30 * DAY_MS
private const val YEAR_MS = 365 * DAY_MS

@Serializable
private data class TimelinePointJson(val daysSinceStart: Int, val level: Int)

private data class WindowedCounts(val today: Int, val week: Int, val month: Int, val year: Int, val allTime: Int)

private fun computeActivityBuckets(isoTimestamps: List<String?>, nowMillis: Long): ActivityBuckets {
    val nowDays = nowMillis / DAY_MS
    val nowDt = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(TimeZone.currentSystemDefault())
    val nowTotalMonths = nowDt.year * 12 + (nowDt.monthNumber - 1)

    val weekDays = IntArray(7)
    val monthDays = IntArray(30)
    val yearMonths = IntArray(12)

    for (ts in isoTimestamps) {
        val tsMillis = ts?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() } ?: continue
        val tsDays = tsMillis / DAY_MS
        val daysAgo = (nowDays - tsDays).toInt()
        if (daysAgo in 0..6) weekDays[6 - daysAgo]++
        if (daysAgo in 0..29) monthDays[29 - daysAgo]++
        val tsDt = Instant.fromEpochMilliseconds(tsMillis).toLocalDateTime(TimeZone.currentSystemDefault())
        val monthsAgo = nowTotalMonths - (tsDt.year * 12 + (tsDt.monthNumber - 1))
        if (monthsAgo in 0..11) yearMonths[11 - monthsAgo]++
    }

    return ActivityBuckets(weekDays.toList(), monthDays.toList(), yearMonths.toList())
}

private fun computeWindowedCounts(isoTimestamps: List<String?>, nowMillis: Long): WindowedCounts {
    val millis = isoTimestamps.mapNotNull { ts ->
        ts?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() }
    }
    return WindowedCounts(
        today = millis.count { nowMillis - it < DAY_MS },
        week = millis.count { nowMillis - it < WEEK_MS },
        month = millis.count { nowMillis - it < MONTH_MS },
        year = millis.count { nowMillis - it < YEAR_MS },
        allTime = millis.size
    )
}

class FriendStatsRepository(
    private val friendRepository: FriendRepository,
    private val friendStatsDao: FriendStatsDao,
    private val json: Json,
    private val selfAssignmentDao: AssignmentDao,
    private val selfReviewStatisticDao: ReviewStatisticDao,
    private val selfLevelProgressionDao: LevelProgressionDao
) {
    // Pre-built self-stats flow; shared across all observeLeaderboard subscriptions so Room
    // doesn't open duplicate queries when metric/window changes (only the re-sort changes).
    private val selfStatsFlow: Flow<FriendStats> = combine(
        selfAssignmentDao.observeAllBurnedTimestamps(),
        selfAssignmentDao.observeAllStartedTimestamps(),
        selfReviewStatisticDao.observeAll(),
        selfLevelProgressionDao.observeAll()
    ) { burnedTs, startedTs, statistics, progressions ->
        buildSelfStats(burnedTs, startedTs, statistics, progressions)
    }.flowOn(Dispatchers.Default)

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
        }.flowOn(Dispatchers.Default)

    suspend fun refreshAllIfStale() {
        val friends = friendRepository.friendsFlow.first()
        coroutineScope {
            friends.map { entry ->
                async {
                    val cached = friendStatsDao.getById(entry.id)
                    val nowMillis = Clock.System.now().toEpochMilliseconds()
                    val isStale = cached == null ||
                        (nowMillis - cached.fetchedAtMillis) > FRIEND_STATS_TTL.inWholeMilliseconds
                    if (isStale) refreshFriend(entry)
                }
            }.awaitAll()
        }
    }

    suspend fun refreshFriend(entry: FriendEntry) {
        val token = friendRepository.decryptToken(entry)
        val api = createFriendWaniKaniApi(token, json)
        val entity = fetchFriendStats(entry.id, api) ?: return
        friendStatsDao.upsert(entity)
    }

    suspend fun removeFriendCache(id: String) {
        friendStatsDao.deleteById(id)
    }

    private suspend fun fetchFriendStats(friendId: String, api: WaniKaniApi): FriendStatsEntity? {
        val userResult = safeApiCall { api.getUser() }
        val userData = (userResult as? ApiResult.Success)?.data?.data ?: return null

        val nowMillis = Clock.System.now().toEpochMilliseconds()
        val yearAgoIso = Instant.fromEpochMilliseconds(nowMillis - YEAR_MS).toString()

        // All burned assignments → all-time + windowed burned counts
        val burnedResult = safeApiCall {
            collectAllPages(
                firstPage = { api.getAssignments(burned = true) },
                nextPage = { url -> api.getAssignmentsPage(url) }
            )
        }
        val burnedItems = (burnedResult as? ApiResult.Success)?.data ?: emptyList()
        val burnedTimestamps = burnedItems.map { it.data.burnedAt }
        val burnedCounts = computeWindowedCounts(burnedTimestamps, nowMillis)
            .let { it.copy(allTime = burnedItems.size) }
        val burnedBuckets = computeActivityBuckets(burnedTimestamps, nowMillis)

        // Assignments started in the last year → windowed learned counts
        val learnedResult = safeApiCall {
            collectAllPages(
                firstPage = { api.getAssignments(started = true, startedAfter = yearAgoIso) },
                nextPage = { url -> api.getAssignmentsPage(url) }
            )
        }
        val learnedItems = (learnedResult as? ApiResult.Success)?.data ?: emptyList()
        val learnedTimestamps = learnedItems.map { it.data.startedAt }
        val learnedCounts = computeWindowedCounts(learnedTimestamps, nowMillis)
        val learnedBuckets = computeActivityBuckets(learnedTimestamps, nowMillis)

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
        val accuracy = if (totalAttempts > 0) totalCorrect / totalAttempts else -1f
        val totalReviews = totalAttempts

        // learnedAllTime proxy: number of subjects with review statistics
        val learnedAllTime = statsItems.size

        // Level progressions → timeline + avg speed
        val progressionsResult = safeApiCall { api.getLevelProgressions() }
        val sortedProgressions = (progressionsResult as? ApiResult.Success)?.data?.data
            ?.mapNotNull { item -> item.data.unlockedAt?.let { item.data.level to it } }
            ?.sortedBy { it.second }
            ?: emptyList()

        val avgDaysPerLevel = computeAvgDaysPerLevel(sortedProgressions)
        val daysSinceStart = sortedProgressions.firstOrNull()?.let { (_, unlockedAt) ->
            val startMillis = parseIsoToMillis(unlockedAt)
            if (startMillis != null) ((nowMillis - startMillis) / DAY_MS).toInt() else null
        } ?: -1

        val timelineJson = json.encodeToString(
            ListSerializer(TimelinePointJson.serializer()),
            buildTimeline(sortedProgressions)
        )

        return FriendStatsEntity(
            friendId = friendId,
            username = userData.username,
            level = userData.level,
            reviewAccuracy = accuracy,
            avgDaysPerLevel = avgDaysPerLevel ?: -1f,
            daysSinceStart = daysSinceStart,
            levelTimelineJson = timelineJson,
            fetchedAtMillis = nowMillis,
            learnedToday = learnedCounts.today,
            learnedWeek = learnedCounts.week,
            learnedMonth = learnedCounts.month,
            learnedYear = learnedCounts.year,
            learnedAllTime = learnedAllTime,
            burnedToday = burnedCounts.today,
            burnedWeek = burnedCounts.week,
            burnedMonth = burnedCounts.month,
            burnedYear = burnedCounts.year,
            burnedAllTime = burnedCounts.allTime,
            totalReviews = totalReviews,
            learnedBucketsJson = json.encodeToString(ActivityBuckets.serializer(), learnedBuckets),
            burnedBucketsJson = json.encodeToString(ActivityBuckets.serializer(), burnedBuckets)
        )
    }

    private fun buildSelfStats(
        burnedTimestamps: List<String>,
        startedTimestamps: List<String>,
        statistics: List<ReviewStatisticEntity>,
        progressions: List<LevelProgressionEntity>
    ): FriendStats {
        val nowMillis = Clock.System.now().toEpochMilliseconds()

        val burnedCounts = computeWindowedCounts(burnedTimestamps, nowMillis)
        val learnedCounts = computeWindowedCounts(startedTimestamps, nowMillis)
            .let { it.copy(allTime = statistics.size) }  // proxy: subjects with review history
        val learnedBuckets = computeActivityBuckets(startedTimestamps, nowMillis)
        val burnedBuckets = computeActivityBuckets(burnedTimestamps, nowMillis)

        val totalCorrect = statistics.sumOf { it.meaningCorrect + it.readingCorrect }.toFloat()
        val totalAttempts = statistics.sumOf {
            it.meaningCorrect + it.meaningIncorrect + it.readingCorrect + it.readingIncorrect
        }
        val accuracy = if (totalAttempts > 0) totalCorrect / totalAttempts else -1f

        val sortedProgressions = progressions
            .mapNotNull { p -> p.unlockedAt?.let { p.level to it } }
            .sortedBy { it.second }

        val avgDays = computeAvgDaysPerLevel(sortedProgressions)
        val daysSinceStart = sortedProgressions.firstOrNull()?.let { (_, unlockedAt) ->
            val startMillis = parseIsoToMillis(unlockedAt)
            if (startMillis != null) ((nowMillis - startMillis) / DAY_MS).toInt() else null
        }
        val timeline = buildTimeline(sortedProgressions)
            .map { LevelTimelinePoint(it.daysSinceStart, it.level) }

        return FriendStats(
            friendEntryId = "",
            nickname = "You",
            username = "",
            level = progressions.maxOfOrNull { it.level } ?: 0,
            reviewAccuracy = accuracy,
            avgDaysPerLevel = avgDays,
            daysSinceStart = daysSinceStart,
            levelTimeline = timeline,
            isCurrentUser = true,
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

    private fun computeAvgDaysPerLevel(sortedProgressions: List<Pair<Int, String>>): Float? {
        if (sortedProgressions.size < 2) return null
        val millis = sortedProgressions.mapNotNull { (_, ts) -> parseIsoToMillis(ts) }
        if (millis.size < 2) return null
        val intervals = millis.zipWithNext().map { (a, b) -> (b - a).toFloat() / DAY_MS }
        return intervals.average().toFloat()
    }

    private fun buildTimeline(sortedProgressions: List<Pair<Int, String>>): List<TimelinePointJson> {
        if (sortedProgressions.isEmpty()) return emptyList()
        val startMillis = parseIsoToMillis(sortedProgressions.first().second) ?: return emptyList()
        return sortedProgressions.mapNotNull { (level, ts) ->
            val ms = parseIsoToMillis(ts) ?: return@mapNotNull null
            TimelinePointJson(daysSinceStart = ((ms - startMillis) / DAY_MS).toInt(), level = level)
        }
    }

    private fun parseIsoToMillis(iso: String): Long? =
        runCatching { Instant.parse(iso).toEpochMilliseconds() }.getOrNull()
}
