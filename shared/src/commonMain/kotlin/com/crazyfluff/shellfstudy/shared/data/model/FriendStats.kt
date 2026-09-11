package com.crazyfluff.shellfstudy.shared.data.model

import kotlinx.serialization.Serializable

data class LevelTimelinePoint(val daysSinceStart: Int, val level: Int)

@Serializable
data class ActivityBuckets(
    val weekDays: List<Int> = List(7) { 0 },       // index 0 = 6 days ago, index 6 = today
    val monthDays: List<Int> = List(30) { 0 },     // index 0 = 29 days ago, index 29 = today
    val yearMonths: List<Int> = List(12) { 0 },    // index 0 = 11 months ago, index 11 = current month
    val allTimeMonths: List<Int> = emptyList()     // index 0 = earliest month, last index = current month
)

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

enum class LeaderboardMetric(val displayName: String) {
    LEARNED("Lessons"),
    LEVEL("Level"),
    BURNED("Burned"),
    ACCURACY("Accuracy")
}

enum class LeaderboardWindow(val label: String) {
    WEEK("Week"),
    MONTH("Month"),
    YEAR("Year"),
    ALL_TIME("All time")
}

/**
 * Roster position of the current user. Friends occupy [friendRosterIndex] slots after this one, so
 * a participant's roster index is stable for as long as they stay in the roster — unlike their rank,
 * which changes with the selected metric/window. The UI maps it to a per-user color.
 */
const val SELF_ROSTER_INDEX = 0

/**
 * Roster index for the friend at [friendIndex] in the friend list's insertion order — the order the
 * friends flow emits, which is also the order the full friends list is rendered in. Computed from
 * the *unfiltered* friend list so a friend whose stats haven't been fetched yet doesn't shift
 * everyone below them onto a different color.
 */
fun friendRosterIndex(friendIndex: Int): Int = friendIndex + 1

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
    val rosterIndex: Int,
    val learned: ActivityStats = ActivityStats(),
    val burned: ActivityStats = ActivityStats(),
    val learnedBuckets: ActivityBuckets = ActivityBuckets(),
    val burnedBuckets: ActivityBuckets = ActivityBuckets()
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
