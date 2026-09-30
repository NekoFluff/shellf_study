package com.crazyfluff.shellfstudy.shared.data.studytime

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** A segment's share of one local clock hour. */
internal data class StudySlice(
    val kind: StudyKind,
    val date: LocalDate,
    val dayOfWeek: DayOfWeek,
    val hour: Int,
    val ms: Long,
    val level: Int?
)

/**
 * Turns raw [StudySegment]s into everything the study-time views show. Pure, so the whole feature's
 * arithmetic is tested in commonTest.
 *
 * Every day-based figure comes from [slice], which cuts segments at local hour boundaries. Days, the
 * hour heatmap and per-level days therefore always agree, and a session running past midnight counts
 * toward both days instead of all landing on the day it started.
 */
object StudyTimeAggregator {
    /** Days of history the pace figures look at, compared against the same span before it. */
    const val PACE_SPAN_DAYS = 14

    /** Days of history the heatmap covers — long enough to show a routine, short enough to follow it
     *  when it changes. */
    const val HEATMAP_DAYS = 90

    /** Fallback pace before any history exists. A review item is usually two questions (meaning and
     *  reading), with time to read the answer and any notes after a miss; a lesson item includes
     *  reading its explanations plus the lesson quiz. Generous on purpose: an estimate that runs
     *  short is worse than one that runs long. */
    const val DEFAULT_REVIEW_MS_PER_ITEM = 20_000L
    const val DEFAULT_LESSON_MS_PER_ITEM = 120_000L

    private const val MS_PER_HOUR = 3_600_000L

    fun report(
        segments: List<StudySegment>,
        today: LocalDate,
        zone: TimeZone,
        window: StudyTimeWindow,
        goalMs: Long,
        currentLevel: Int?
    ): StudyTimeReport {
        val slices = slice(segments, zone)
        val daily = dailyTotals(slices)
        return StudyTimeReport(
            overview = overview(segments, slices, today, zone, goalMs),
            window = window,
            buckets = buckets(daily, today, window),
            stats = periodStats(daily, segments, today, zone, window),
            levels = levelTotals(slices),
            currentLevel = currentLevel,
            heatmap = heatmap(slices, today)
        )
    }

    fun overview(segments: List<StudySegment>, today: LocalDate, zone: TimeZone, goalMs: Long): StudyTimeOverview =
        overview(segments, slice(segments, zone), today, zone, goalMs)

    internal fun slice(segments: List<StudySegment>, zone: TimeZone): List<StudySlice> = buildList {
        for (segment in segments) {
            var cursorMs = segment.startedAtMs
            val endMs = segment.startedAtMs + segment.durationMs
            while (cursorMs < endMs) {
                val local = Instant.fromEpochMilliseconds(cursorMs).toLocalDateTime(zone)
                // Measured from the local wall clock rather than epoch hours, so half-hour zones
                // (India, Newfoundland) still cut on their own hour boundaries.
                val intoHourMs = local.minute * 60_000L + local.second * 1_000L + local.nanosecond / 1_000_000
                val nextMs = minOf(cursorMs + MS_PER_HOUR - intoHourMs, endMs)
                val date = local.date
                add(StudySlice(segment.kind, date, date.dayOfWeek, local.hour, nextMs - cursorMs, segment.level))
                cursorMs = nextMs
            }
        }
    }

    internal fun dailyTotals(slices: List<StudySlice>): Map<LocalDate, StudyTimeSplit> {
        val totals = mutableMapOf<LocalDate, StudyTimeSplit>()
        for (slice in slices) {
            totals[slice.date] = (totals[slice.date] ?: StudyTimeSplit.ZERO).plus(slice.kind, slice.ms)
        }
        return totals
    }

    /** Oldest first, zero-filled, the last bucket ending today. */
    internal fun buckets(
        daily: Map<LocalDate, StudyTimeSplit>,
        today: LocalDate,
        window: StudyTimeWindow
    ): List<StudyTimeBucket> =
        (window.bucketCount - 1 downTo 0).map { bucketsAgo ->
            val start = today.minus(bucketsAgo * window.daysPerBucket + window.daysPerBucket - 1, DateTimeUnit.DAY)
            StudyTimeBucket(start, sumDays(daily, start, window.daysPerBucket))
        }

    /** Time comes from the day totals; item counts come from whole segments, counted toward the day
     *  each one started (the counts can't be split across a midnight the way time can). */
    internal fun periodStats(
        daily: Map<LocalDate, StudyTimeSplit>,
        segments: List<StudySegment>,
        today: LocalDate,
        zone: TimeZone,
        window: StudyTimeWindow
    ): StudyTimePeriodStats {
        val days = window.days
        val start = today.minus(days - 1, DateTimeUnit.DAY)
        val total = sumDays(daily, start, days)
        val inWindow = segments.filter { segment ->
            Instant.fromEpochMilliseconds(segment.startedAtMs).toLocalDateTime(zone).date in start..today
        }
        fun itemsOf(kind: StudyKind) = inWindow.filter { it.kind == kind }.sumOf { it.itemsCompleted }
        return StudyTimePeriodStats(
            total = total,
            dailyAverageMs = total.totalMs / days,
            reviewItems = itemsOf(StudyKind.REVIEW),
            lessonItems = itemsOf(StudyKind.LESSON),
            previousTotalMs = sumDays(daily, start.minus(days, DateTimeUnit.DAY), days).totalMs
        )
    }

    /**
     * Time per completed item over the last [PACE_SPAN_DAYS] against the span before, plus over
     * everything recorded (what the all-time estimate prices a history with). Lesson time
     * includes reading the explanations, not just the quiz, so it answers "what does a lesson really
     * cost me". Segments count toward the span they started in; pace needs the item counts, which
     * belong to the whole segment.
     */
    internal fun pace(segments: List<StudySegment>, today: LocalDate, zone: TimeZone): StudyPace {
        val recentStart = today.minus(PACE_SPAN_DAYS - 1, DateTimeUnit.DAY)
        val previousStart = recentStart.minus(PACE_SPAN_DAYS, DateTimeUnit.DAY)
        val byStartDate = segments.groupBy { Instant.fromEpochMilliseconds(it.startedAtMs).toLocalDateTime(zone).date }
        fun msPerItem(matching: List<StudySegment>): Long? {
            val items = matching.sumOf { it.itemsCompleted }
            return if (items <= 0) null else matching.sumOf { it.durationMs } / items
        }
        fun msPerItem(kind: StudyKind, from: LocalDate, until: LocalDate): Long? =
            msPerItem(byStartDate.filterKeys { it in from..until }.values.flatten().filter { it.kind == kind })
        val previousEnd = recentStart.minus(1, DateTimeUnit.DAY)
        return StudyPace(
            reviewMsPerItem = msPerItem(StudyKind.REVIEW, recentStart, today),
            lessonMsPerItem = msPerItem(StudyKind.LESSON, recentStart, today),
            previousReviewMsPerItem = msPerItem(StudyKind.REVIEW, previousStart, previousEnd),
            previousLessonMsPerItem = msPerItem(StudyKind.LESSON, previousStart, previousEnd),
            allTimeReviewMsPerItem = msPerItem(segments.filter { it.kind == StudyKind.REVIEW }),
            allTimeLessonMsPerItem = msPerItem(segments.filter { it.kind == StudyKind.LESSON })
        )
    }

    /** Highest level first. Stretches recorded before the level was known are left out. */
    internal fun levelTotals(slices: List<StudySlice>): List<LevelStudyTime> =
        slices.filter { it.level != null }
            .groupBy { it.level!! }
            .map { (level, levelSlices) ->
                LevelStudyTime(
                    level = level,
                    split = levelSlices.fold(StudyTimeSplit.ZERO) { acc, slice -> acc.plus(slice.kind, slice.ms) },
                    activeDays = levelSlices.map { it.date }.distinct().size
                )
            }
            .sortedByDescending { it.level }

    internal fun heatmap(slices: List<StudySlice>, today: LocalDate): StudyHeatmap {
        val since = today.minus(HEATMAP_DAYS - 1, DateTimeUnit.DAY)
        val cells = LongArray(7 * StudyHeatmap.HOURS)
        for (slice in slices) {
            if (slice.date < since || slice.date > today) continue
            cells[StudyHeatmap.index(slice.dayOfWeek, slice.hour)] += slice.ms
        }
        return StudyHeatmap(cells.toList())
    }

    /** Like the study streak: today counts once it's met, and not meeting it yet doesn't break it. */
    internal fun goalStreak(daily: Map<LocalDate, StudyTimeSplit>, today: LocalDate, goalMs: Long): Int {
        if (goalMs <= 0L) return 0
        fun met(date: LocalDate) = (daily[date]?.totalMs ?: 0L) >= goalMs
        var cursor = if (met(today)) today else today.minus(1, DateTimeUnit.DAY)
        var streak = 0
        while (met(cursor)) {
            streak++
            cursor = cursor.minus(1, DateTimeUnit.DAY)
        }
        return streak
    }

}

private fun overview(
    segments: List<StudySegment>,
    slices: List<StudySlice>,
    today: LocalDate,
    zone: TimeZone,
    goalMs: Long
): StudyTimeOverview {
    val daily = StudyTimeAggregator.dailyTotals(slices)
    return StudyTimeOverview(
        today = daily[today] ?: StudyTimeSplit.ZERO,
        goalMs = goalMs,
        goalStreakDays = StudyTimeAggregator.goalStreak(daily, today, goalMs),
        lastSevenDays = StudyTimeAggregator.buckets(daily, today, StudyTimeWindow.WEEK),
        pace = StudyTimeAggregator.pace(segments, today, zone),
        hasAnyData = segments.isNotEmpty(),
        levelTotalsMs = slices.mapNotNull { slice -> slice.level?.let { it to slice.ms } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, ms) -> ms.sum() }
    )
}

private fun sumDays(daily: Map<LocalDate, StudyTimeSplit>, start: LocalDate, days: Int): StudyTimeSplit =
    (0 until days).fold(StudyTimeSplit.ZERO) { acc, offset ->
        acc + (daily[start.plus(offset, DateTimeUnit.DAY)] ?: StudyTimeSplit.ZERO)
    }
