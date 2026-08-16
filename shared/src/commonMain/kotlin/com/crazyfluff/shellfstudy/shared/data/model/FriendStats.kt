package com.crazyfluff.shellfstudy.shared.data.model

data class LevelTimelinePoint(val daysSinceStart: Int, val level: Int)

data class FriendStats(
    val friendEntryId: String,
    val nickname: String,
    val username: String,
    val level: Int,
    val burnedCount: Int,
    val reviewAccuracy: Float,
    val avgDaysPerLevel: Float?,
    val daysSinceStart: Int?,
    val levelTimeline: List<LevelTimelinePoint>,
    val isCurrentUser: Boolean
)

enum class LeaderboardMetric { LEVEL, BURNED, ACCURACY, SPEED }

data class Leaderboard(
    val entries: List<FriendStats>,
    val metric: LeaderboardMetric,
    val selfRank: Int?
) {
    fun sorted(by: LeaderboardMetric): Leaderboard {
        val sorted = when (by) {
            LeaderboardMetric.LEVEL -> entries.sortedByDescending { it.level }
            LeaderboardMetric.BURNED -> entries.sortedByDescending { it.burnedCount }
            LeaderboardMetric.ACCURACY -> entries.sortedByDescending { it.reviewAccuracy }
            LeaderboardMetric.SPEED -> entries.sortedWith(
                compareBy(nullsLast()) { it.avgDaysPerLevel }
            )
        }
        val selfIndex = sorted.indexOfFirst { it.isCurrentUser }
        return copy(entries = sorted, metric = by, selfRank = if (selfIndex >= 0) selfIndex + 1 else null)
    }
}
