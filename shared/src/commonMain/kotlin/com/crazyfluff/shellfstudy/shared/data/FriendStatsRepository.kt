package com.crazyfluff.shellfstudy.shared.data

import com.crazyfluff.shellfstudy.shared.data.model.FriendEntry
import com.crazyfluff.shellfstudy.shared.data.model.FriendStats
import com.crazyfluff.shellfstudy.shared.data.model.Leaderboard
import com.crazyfluff.shellfstudy.shared.data.model.LeaderboardMetric
import com.crazyfluff.shellfstudy.shared.data.model.LevelTimelinePoint
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val FRIEND_STATS_TTL = 30.minutes

@Serializable
private data class TimelinePointJson(val daysSinceStart: Int, val level: Int)

class FriendStatsRepository(
    private val friendRepository: FriendRepository,
    private val friendStatsDao: FriendStatsDao,
    private val json: Json,
    private val selfAssignmentDao: AssignmentDao,
    private val selfReviewStatisticDao: ReviewStatisticDao,
    private val selfLevelProgressionDao: LevelProgressionDao
) {
    fun observeLeaderboard(metric: LeaderboardMetric = LeaderboardMetric.LEVEL): Flow<Leaderboard?> =
        combine(
            friendRepository.friendsFlow,
            friendStatsDao.observeAll(),
            selfAssignmentDao.observeBurnedCount(),
            selfReviewStatisticDao.observeAll(),
            selfLevelProgressionDao.observeAll()
        ) { friends, cachedStats, selfBurned, selfStatistics, selfProgressions ->
            if (friends.isEmpty()) return@combine null

            val statsByFriendId = cachedStats.associateBy { it.friendId }
            val friendEntries = friends.mapNotNull { entry ->
                statsByFriendId[entry.id]?.toFriendStats(nickname = entry.nickname)
            }

            val selfStats = buildSelfStats(selfBurned, selfStatistics, selfProgressions)
            val all = listOf(selfStats) + friendEntries
            Leaderboard(entries = all, metric = metric, selfRank = null).sorted(metric)
        }

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

        val burnedResult = safeApiCall {
            collectAllPages(
                firstPage = { api.getAssignments(burned = true) },
                nextPage = { url -> api.getAssignmentsPage(url) }
            )
        }
        val burnedCount = (burnedResult as? ApiResult.Success)?.data?.size ?: 0

        val statsResult = safeApiCall {
            collectAllPages(
                firstPage = { api.getReviewStatistics() },
                nextPage = { url -> api.getReviewStatisticsPage(url) }
            )
        }
        val accuracy = (statsResult as? ApiResult.Success)?.data?.let { items ->
            val totalCorrect = items.sumOf { it.data.meaningCorrect + it.data.readingCorrect }.toFloat()
            val totalAttempts = items.sumOf {
                it.data.meaningCorrect + it.data.meaningIncorrect +
                    it.data.readingCorrect + it.data.readingIncorrect
            }.toFloat()
            if (totalAttempts > 0f) totalCorrect / totalAttempts else -1f
        } ?: -1f

        val progressionsResult = safeApiCall { api.getLevelProgressions() }
        // getLevelProgressions() → WkCollectionResponse<LevelProgressionData>; .data is the item list
        val sortedProgressions = (progressionsResult as? ApiResult.Success)?.data?.data
            ?.mapNotNull { item -> item.data.unlockedAt?.let { item.data.level to it } }
            ?.sortedBy { it.second }
            ?: emptyList()

        val avgDaysPerLevel = computeAvgDaysPerLevel(sortedProgressions)
        val daysSinceStart = sortedProgressions.firstOrNull()?.let { (_, unlockedAt) ->
            val startMillis = parseIsoToMillis(unlockedAt)
            if (startMillis != null) {
                ((Clock.System.now().toEpochMilliseconds() - startMillis) / 86_400_000L).toInt()
            } else null
        } ?: -1

        val timelinePoints = buildTimeline(sortedProgressions)
        val timelineJson = json.encodeToString(
            ListSerializer(TimelinePointJson.serializer()), timelinePoints
        )

        return FriendStatsEntity(
            friendId = friendId,
            username = userData.username,
            level = userData.level,
            burnedCount = burnedCount,
            reviewAccuracy = accuracy,
            avgDaysPerLevel = avgDaysPerLevel ?: -1f,
            daysSinceStart = daysSinceStart,
            levelTimelineJson = timelineJson,
            fetchedAtMillis = Clock.System.now().toEpochMilliseconds()
        )
    }

    private fun buildSelfStats(
        burnedCount: Int,
        statistics: List<ReviewStatisticEntity>,
        progressions: List<LevelProgressionEntity>
    ): FriendStats {
        val totalCorrect = statistics.sumOf { it.meaningCorrect + it.readingCorrect }.toFloat()
        val totalAttempts = statistics.sumOf {
            it.meaningCorrect + it.meaningIncorrect + it.readingCorrect + it.readingIncorrect
        }.toFloat()
        val accuracy = if (totalAttempts > 0f) totalCorrect / totalAttempts else -1f

        val sortedProgressions = progressions
            .mapNotNull { p -> p.unlockedAt?.let { p.level to it } }
            .sortedBy { it.second }

        val avgDays = computeAvgDaysPerLevel(sortedProgressions)
        val daysSinceStart = sortedProgressions.firstOrNull()?.let { (_, unlockedAt) ->
            val startMillis = parseIsoToMillis(unlockedAt)
            if (startMillis != null) {
                ((Clock.System.now().toEpochMilliseconds() - startMillis) / 86_400_000L).toInt()
            } else null
        }
        val timeline = buildTimeline(sortedProgressions)
            .map { LevelTimelinePoint(it.daysSinceStart, it.level) }

        return FriendStats(
            friendEntryId = "",
            nickname = "You",
            username = "",
            level = progressions.maxOfOrNull { it.level } ?: 0,
            burnedCount = burnedCount,
            reviewAccuracy = accuracy,
            avgDaysPerLevel = avgDays,
            daysSinceStart = daysSinceStart,
            levelTimeline = timeline,
            isCurrentUser = true
        )
    }

    private fun FriendStatsEntity.toFriendStats(nickname: String): FriendStats {
        val timeline = runCatching {
            json.decodeFromString(ListSerializer(TimelinePointJson.serializer()), levelTimelineJson)
                .map { LevelTimelinePoint(it.daysSinceStart, it.level) }
        }.getOrDefault(emptyList())
        return FriendStats(
            friendEntryId = friendId,
            nickname = nickname,
            username = username,
            level = level,
            burnedCount = burnedCount,
            reviewAccuracy = reviewAccuracy,
            avgDaysPerLevel = if (avgDaysPerLevel < 0f) null else avgDaysPerLevel,
            daysSinceStart = if (daysSinceStart < 0) null else daysSinceStart,
            levelTimeline = timeline,
            isCurrentUser = false
        )
    }

    private fun computeAvgDaysPerLevel(sortedProgressions: List<Pair<Int, String>>): Float? {
        if (sortedProgressions.size < 2) return null
        val millis = sortedProgressions.mapNotNull { (_, ts) -> parseIsoToMillis(ts) }
        if (millis.size < 2) return null
        val intervals = millis.zipWithNext().map { (a, b) -> (b - a).toFloat() / 86_400_000f }
        return intervals.average().toFloat()
    }

    private fun buildTimeline(sortedProgressions: List<Pair<Int, String>>): List<TimelinePointJson> {
        if (sortedProgressions.isEmpty()) return emptyList()
        val startMillis = parseIsoToMillis(sortedProgressions.first().second) ?: return emptyList()
        return sortedProgressions.mapNotNull { (level, ts) ->
            val ms = parseIsoToMillis(ts) ?: return@mapNotNull null
            TimelinePointJson(daysSinceStart = ((ms - startMillis) / 86_400_000L).toInt(), level = level)
        }
    }

    private fun parseIsoToMillis(iso: String): Long? =
        runCatching { Instant.parse(iso).toEpochMilliseconds() }.getOrNull()
}
