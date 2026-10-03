package com.crazyfluff.shellfstudy.shared.data.studytime

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

enum class StudyKind { LESSON, REVIEW }

/** One recorded stretch of active study — see
 *  [com.crazyfluff.shellfstudy.shared.database.studytime.StudyTimeSegmentEntity]. */
data class StudySegment(
    val kind: StudyKind,
    val startedAtMs: Long,
    val durationMs: Long,
    val level: Int?,
    val itemsCompleted: Int
)

/** Time split between lessons and reviews. */
data class StudyTimeSplit(val lessonMs: Long = 0L, val reviewMs: Long = 0L) {
    val totalMs: Long get() = lessonMs + reviewMs

    operator fun plus(other: StudyTimeSplit) = StudyTimeSplit(lessonMs + other.lessonMs, reviewMs + other.reviewMs)

    fun plus(kind: StudyKind, ms: Long) = when (kind) {
        StudyKind.LESSON -> copy(lessonMs = lessonMs + ms)
        StudyKind.REVIEW -> copy(reviewMs = reviewMs + ms)
    }

    companion object {
        val ZERO = StudyTimeSplit()
    }
}

/** Review and lesson items finished. */
data class StudyItemCounts(val reviews: Int = 0, val lessons: Int = 0) {
    operator fun plus(other: StudyItemCounts) = StudyItemCounts(reviews + other.reviews, lessons + other.lessons)

    fun plus(kind: StudyKind, items: Int) = when (kind) {
        StudyKind.LESSON -> copy(lessons = lessons + items)
        StudyKind.REVIEW -> copy(reviews = reviews + items)
    }

    companion object {
        val ZERO = StudyItemCounts()
    }
}

/** A chart bar: one day, or for [StudyTimeWindow.YEAR] the 7 days starting at [start]. [items] counts
 *  each session toward the day it started, like [StudyTimePeriodStats]. */
data class StudyTimeBucket(
    val start: LocalDate,
    val split: StudyTimeSplit,
    val items: StudyItemCounts = StudyItemCounts.ZERO
)

/** How far back the chart and period stats look. Every window ends today. */
enum class StudyTimeWindow(val bucketCount: Int, val daysPerBucket: Int) {
    WEEK(bucketCount = 7, daysPerBucket = 1),
    MONTH(bucketCount = 30, daysPerBucket = 1),
    YEAR(bucketCount = 52, daysPerBucket = 7);

    val days: Int get() = bucketCount * daysPerBucket
}

data class StudyTimePeriodStats(
    val total: StudyTimeSplit,
    /** Averaged over every day in the window, not only active ones — a rest day is part of the habit. */
    val dailyAverageMs: Long,
    /** Review items finished in the window — what the review time produced. */
    val reviewItems: Int,
    /** Lesson items learned in the window. */
    val lessonItems: Int,
    val previousTotalMs: Long
) {
    /** Relative change against the window before this one, or null when there's nothing to compare. */
    val changeFraction: Double?
        get() = if (previousTotalMs <= 0L) null else (total.totalMs - previousTotalMs).toDouble() / previousTotalMs
}

/** Milliseconds of study per completed item: recent, the span before it, and over everything
 *  recorded. Null means no data. */
data class StudyPace(
    val reviewMsPerItem: Long? = null,
    val lessonMsPerItem: Long? = null,
    val previousReviewMsPerItem: Long? = null,
    val previousLessonMsPerItem: Long? = null,
    /** Over every recorded session: steadier than the recent figures, for pricing a whole history. */
    val allTimeReviewMsPerItem: Long? = null,
    val allTimeLessonMsPerItem: Long? = null
)

data class LevelStudyTime(val level: Int, val split: StudyTimeSplit, val activeDays: Int)

/** Study time per weekday × hour of day, Monday first. */
data class StudyHeatmap(val cellsMs: List<Long>) {
    fun at(dayOfWeek: DayOfWeek, hour: Int): Long = cellsMs[index(dayOfWeek, hour)]

    val maxMs: Long get() = cellsMs.maxOrNull() ?: 0L

    val isEmpty: Boolean get() = maxMs == 0L

    /** When in the day most study time falls, or null with no data. */
    val peakPartOfDay: PartOfDay?
        get() = if (isEmpty) {
            null
        } else {
            PartOfDay.entries.maxBy { part -> part.hours.sumOf { hour -> hourTotalMs(hour) } }
        }

    /** Whether weekdays or weekends get more time per day, or null with no data. Per day, since
     *  there are five weekdays to two weekend days. */
    val prefersWeekends: Boolean?
        get() {
            if (isEmpty) return null
            val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
            fun perDay(days: List<DayOfWeek>) = days.sumOf { day -> (0 until HOURS).sumOf { at(day, it) } } / days.size
            val (weekendDays, weekdays) = DayOfWeek.entries.partition { it in weekend }
            return perDay(weekendDays) > perDay(weekdays)
        }

    private fun hourTotalMs(hour: Int) = DayOfWeek.entries.sumOf { at(it, hour) }

    companion object {
        const val HOURS = 24

        fun index(dayOfWeek: DayOfWeek, hour: Int) = (dayOfWeek.isoDayNumber - 1) * HOURS + hour
    }
}

private const val MORNING_STARTS_AT = 5
private const val AFTERNOON_STARTS_AT = 12
private const val EVENING_STARTS_AT = 17
private const val NIGHT_STARTS_AT = 21

/** [phrase] completes "You study most …". */
enum class PartOfDay(val phrase: String, val hours: List<Int>) {
    MORNING("in the mornings", (MORNING_STARTS_AT until AFTERNOON_STARTS_AT).toList()),
    AFTERNOON("in the afternoons", (AFTERNOON_STARTS_AT until EVENING_STARTS_AT).toList()),
    EVENING("in the evenings", (EVENING_STARTS_AT until NIGHT_STARTS_AT).toList()),
    NIGHT("late at night", (NIGHT_STARTS_AT until StudyHeatmap.HOURS).toList() + (0 until MORNING_STARTS_AT).toList())
}

/** What the dashboard card and the screen's hero both show. */
data class StudyTimeOverview(
    val today: StudyTimeSplit,
    val goalMs: Long,
    val lastSevenDays: List<StudyTimeBucket>,
    val pace: StudyPace,
    val hasAnyData: Boolean,
    /** Recorded time per WaniKani level, for "time on this level so far". */
    val levelTotalsMs: Map<Int, Long> = emptyMap()
) {
    val goalFraction: Float get() = if (goalMs <= 0L) 0f else (today.totalMs.toFloat() / goalMs).coerceAtMost(1f)
}

data class StudyTimeReport(
    val overview: StudyTimeOverview,
    val window: StudyTimeWindow,
    val buckets: List<StudyTimeBucket>,
    val stats: StudyTimePeriodStats,
    /** Highest level first. */
    val levels: List<LevelStudyTime>,
    val currentLevel: Int?,
    val heatmap: StudyHeatmap,
    /** The whole WaniKani history priced at the learner's pace; null before there is any. */
    val lifetime: LifetimeEstimate? = null
)
