package com.crazyfluff.shellfstudy.shared.data.model

data class LevelTimelinePoint(val daysSinceStart: Int, val level: Int)

data class ActivityStats(
    val today: Int = 0,
    val week: Int = 0,
    val month: Int = 0,
    val year: Int = 0,
    val allTime: Int = 0
) {
    fun forWindow(window: LeaderboardWindow): Int = when (window) {
        LeaderboardWindow.WEEK -> week
        LeaderboardWindow.MONTH -> month
        LeaderboardWindow.YEAR -> year
        LeaderboardWindow.ALL_TIME -> allTime
    }
}

enum class LeaderboardMetric { LEARNED, REVIEWS, LEVEL, BURNED, ACCURACY }

enum class LeaderboardWindow { WEEK, MONTH, YEAR, ALL_TIME }

data class FriendStats(
    val friendEntryId: String,
    val nickname: String,
    val username: String,
    val level: Int,
    val reviewAccuracy: Float,
    val avgDaysPerLevel: Float?,
    val daysSinceStart: Int?,
    val levelTimeline: List<LevelTimelinePoint>,
    val isCurrentUser: Boolean,
    val learned: ActivityStats = ActivityStats(),
    val burned: ActivityStats = ActivityStats(),
    val totalReviews: Int = 0
)

data class Leaderboard(
    val entries: List<FriendStats>,
    val metric: LeaderboardMetric,
    val window: LeaderboardWindow,
    val selfRank: Int?
) {
    fun sorted(by: LeaderboardMetric, window: LeaderboardWindow): Leaderboard {
        val sorted = when (by) {
            LeaderboardMetric.LEARNED -> entries.sortedByDescending { it.learned.forWindow(window) }
            LeaderboardMetric.REVIEWS -> entries.sortedByDescending { it.totalReviews }
            LeaderboardMetric.LEVEL -> entries.sortedByDescending { it.level }
            LeaderboardMetric.BURNED -> entries.sortedByDescending { it.burned.forWindow(window) }
            LeaderboardMetric.ACCURACY -> entries.sortedByDescending { it.reviewAccuracy }
        }
        val selfIndex = sorted.indexOfFirst { it.isCurrentUser }
        return copy(
            entries = sorted,
            metric = by,
            window = window,
            selfRank = if (selfIndex >= 0) selfIndex + 1 else null
        )
    }
}
