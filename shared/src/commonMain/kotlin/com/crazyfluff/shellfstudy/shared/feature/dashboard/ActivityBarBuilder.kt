package com.crazyfluff.shellfstudy.shared.feature.dashboard

import com.crazyfluff.shellfstudy.shared.data.model.ActivityBuckets
import com.crazyfluff.shellfstudy.shared.data.model.FriendStats
import com.crazyfluff.shellfstudy.shared.data.model.LeaderboardMetric
import com.crazyfluff.shellfstudy.shared.data.model.LeaderboardWindow
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

private const val DAY_MS = 86_400_000L

internal data class ActivityBar(val label: String, val counts: List<Int>)

internal fun formatShortDate(epochMillis: Long, tz: TimeZone = TimeZone.currentSystemDefault()): String {
    val dt = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(tz)
    return "${dt.monthNumber}/${dt.dayOfMonth}"
}

internal fun levelChartSubtitle(window: LeaderboardWindow): String = when (window) {
    LeaderboardWindow.WEEK -> "Daily — last 7 days"
    LeaderboardWindow.MONTH -> "Daily — last 30 days"
    LeaderboardWindow.YEAR -> "Monthly — last 12 months"
    LeaderboardWindow.ALL_TIME -> "Full progression"
}

internal fun activityChartSubtitle(window: LeaderboardWindow): String = when (window) {
    LeaderboardWindow.WEEK -> "Cumulative — last 7 days"
    LeaderboardWindow.MONTH -> "Cumulative — last 4 weeks"
    LeaderboardWindow.YEAR -> "Cumulative — last 12 months"
    LeaderboardWindow.ALL_TIME -> "Cumulative — all time"
}

internal fun buildActivityBars(
    entries: List<FriendStats>,
    metric: LeaderboardMetric,
    window: LeaderboardWindow,
    nowMillis: Long,
    tz: TimeZone = TimeZone.currentSystemDefault()
): List<ActivityBar> {
    fun buckets(e: FriendStats): ActivityBuckets = when (metric) {
        LeaderboardMetric.LEARNED -> e.learnedBuckets
        else -> e.burnedBuckets
    }

    return when (window) {
        LeaderboardWindow.WEEK -> (0..6).map { i ->
            val daysAgo = 6 - i
            val dayMs = nowMillis - daysAgo * DAY_MS
            ActivityBar(formatShortDate(dayMs, tz), entries.map { buckets(it).weekDays.getOrElse(i) { 0 } })
        }
        LeaderboardWindow.MONTH -> {
            val groupRanges = listOf(0..6, 7..13, 14..20, 21..29)
            val labels = listOf("4w ago", "3w ago", "2w ago", "This wk")
            groupRanges.mapIndexed { gi, range ->
                ActivityBar(labels[gi], entries.map { buckets(it).monthDays.slice(range).sum() })
            }
        }
        LeaderboardWindow.YEAR -> {
            val nowDt = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(tz)
            val nowTotalMonths = nowDt.year * 12 + (nowDt.monthNumber - 1)
            (0..11).map { i ->
                val targetMonth = (nowTotalMonths - (11 - i)) % 12
                ActivityBar(MonthNames.ENGLISH_ABBREVIATED.names[targetMonth], entries.map { buckets(it).yearMonths.getOrElse(i) { 0 } })
            }
        }
        LeaderboardWindow.ALL_TIME -> {
            val nowDt = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(tz)
            val nowTotalMonths = nowDt.year * 12 + (nowDt.monthNumber - 1)
            val allTimeBuckets = entries.map { buckets(it).allTimeMonths }
            val maxLen = allTimeBuckets.maxOfOrNull { it.size }.takeIf { it != null && it > 0 } ?: 12
            if (maxLen <= 12 && allTimeBuckets.all { it.isEmpty() }) {
                return (0..11).map { i ->
                    val targetMonth = (nowTotalMonths - (11 - i)) % 12
                    ActivityBar(MonthNames.ENGLISH_ABBREVIATED.names[targetMonth], entries.map { buckets(it).yearMonths.getOrElse(i) { 0 } })
                }
            }
            val aligned = allTimeBuckets.map { b -> List(maxLen - b.size) { 0 } + b }
            (0 until maxLen).map { i ->
                val monthsAgo = maxLen - 1 - i
                val totalMonths = nowTotalMonths - monthsAgo
                val label = "${MonthNames.ENGLISH_ABBREVIATED.names[totalMonths % 12]} '${(totalMonths / 12 % 100).toString().padStart(2, '0')}"
                ActivityBar(label, aligned.map { it[i] })
            }
        }
    }
}
