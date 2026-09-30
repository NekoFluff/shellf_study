package com.crazyfluff.shellfstudy.shared.data

import com.crazyfluff.shellfstudy.shared.data.model.ActivityBuckets
import com.crazyfluff.shellfstudy.shared.data.model.ActivityStats
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import com.crazyfluff.shellfstudy.shared.data.model.SrsCounts

private val DAY = 1.days

/*
 * The pure arithmetic behind a leaderboard entry — timeline, windowed activity counts, accuracy and
 * pace — shared by a friend's stats (from the API) and your own (from the local mirror).
 */

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
    val reviewAccuracy: Float?,
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

    val accuracy = if (totalAttempts > 0) totalCorrect / totalAttempts else null

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

private const val LAST_APPRENTICE_STAGE = 4
private const val LAST_GURU_STAGE = 6
private const val MASTER_STAGE = 7
private const val ENLIGHTENED_STAGE = 8
private const val BURNED_STAGE = 9

/** [countSrsStagesFromTotals] for one stage per item: a friend's fetched assignments. */
internal fun countSrsStages(stages: List<Int>): SrsCounts =
    countSrsStagesFromTotals(stages.groupingBy { it }.eachCount())

/**
 * Buckets per-stage item counts the way WaniKani's dashboard does: Apprentice 1–4, Guru 5–6,
 * Master 7, Enlightened 8, Burned 9. Stage 0 (unlocked, not started) belongs to none of them.
 * Takes totals so the user's own counts can come straight from a GROUP BY on the local mirror.
 */
internal fun countSrsStagesFromTotals(countByStage: Map<Int, Int>): SrsCounts {
    fun sum(stages: IntRange) = stages.sumOf { countByStage[it] ?: 0 }
    return SrsCounts(
        apprentice = sum(1..LAST_APPRENTICE_STAGE),
        guru = sum((LAST_APPRENTICE_STAGE + 1)..LAST_GURU_STAGE),
        master = sum(MASTER_STAGE..MASTER_STAGE),
        enlightened = sum(ENLIGHTENED_STAGE..ENLIGHTENED_STAGE),
        burned = sum(BURNED_STAGE..BURNED_STAGE)
    )
}
