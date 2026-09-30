package com.crazyfluff.shellfstudy.shared.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
data class LevelTimelinePoint(val daysSinceStart: Int, val level: Int)

@Immutable
@Serializable
data class ActivityBuckets(
    val weekDays: List<Int> = List(7) { 0 },       // index 0 = 6 days ago, index 6 = today
    val monthDays: List<Int> = List(30) { 0 },     // index 0 = 29 days ago, index 29 = today
    val yearMonths: List<Int> = List(12) { 0 },    // index 0 = 11 months ago, index 11 = current month
    val allTimeMonths: List<Int> = emptyList()     // index 0 = earliest month, last index = current month
)

@Immutable
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

/** ACCURACY has full sort/format/chart support below (see LeaderboardCard/RaceChartCard) but is
 *  not one of the selectable chips in LeaderboardCard's `metrics` list, so it's unreachable from
 *  the UI today. Left wired up rather than removed since every other piece of it is real, working
 *  code — add it to `metrics` to expose it, or strip the ACCURACY branches everywhere it's
 *  handled if it's been ruled out for good. */
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

/** How many of someone's started items sit in each SRS group, as WaniKani's dashboard groups them. */
@Immutable
data class SrsCounts(
    val apprentice: Int = 0,
    val guru: Int = 0,
    val master: Int = 0,
    val enlightened: Int = 0,
    val burned: Int = 0
)

@Immutable
data class FriendStats(
    val friendEntryId: String,
    val nickname: String,
    val username: String,
    val level: Int,
    /** Null when the friend has never had a review graded — distinct from 0f, which is a real 0%. */
    val reviewAccuracy: Float?,
    val avgDaysPerLevel: Float?,
    val daysSinceStart: Int?,
    val levelTimeline: List<LevelTimelinePoint>,
    val isCurrentUser: Boolean,
    val rosterIndex: Int,
    val learned: ActivityStats = ActivityStats(),
    val burned: ActivityStats = ActivityStats(),
    val learnedBuckets: ActivityBuckets = ActivityBuckets(),
    val burnedBuckets: ActivityBuckets = ActivityBuckets(),
    /** Started items per SRS group: counted from a friend's fetched assignments, or from the local
     *  mirror for the current user. Null only when unknown. Zeros are a real "none in this group",
     *  so they must not stand in for it. */
    val srsCounts: SrsCounts? = null,
    /** When a friend's figures were fetched, for "Updated 5 min ago". Null for the current user,
     *  whose figures are always live. */
    val fetchedAtMillis: Long? = null
) {
    /**
     * Days on the current level, as of [daysSinceStart]'s reading: the time since the last level-up
     * in [levelTimeline] that reached [level]. Null when the timeline doesn't know when this level
     * started (no progressions, or the level isn't in it).
     */
    val daysOnCurrentLevel: Int?
        get() {
            val now = daysSinceStart ?: return null
            val levelStart = levelTimeline.lastOrNull { it.level == level } ?: return null
            return (now - levelStart.daysSinceStart).coerceAtLeast(0)
        }
}

@Immutable
data class Leaderboard(
    val entries: List<FriendStats>,
    val metric: LeaderboardMetric,
    val window: LeaderboardWindow
) {
    fun sorted(by: LeaderboardMetric, window: LeaderboardWindow): Leaderboard {
        val sorted = when (by) {
            LeaderboardMetric.LEARNED -> entries.sortedByDescending { it.learned.forWindow(window) }
            LeaderboardMetric.LEVEL -> entries.sortedByDescending { it.level }
            LeaderboardMetric.BURNED -> entries.sortedByDescending { it.burned.forWindow(window) }
            // sortedByDescending treats null as smaller than any value, so a friend with no reviews
            // ranks below one with a real 0% rather than being given a made-up number to sort by.
            LeaderboardMetric.ACCURACY -> entries.sortedByDescending { it.reviewAccuracy }
        }
        return copy(entries = sorted, metric = by, window = window)
    }
}
