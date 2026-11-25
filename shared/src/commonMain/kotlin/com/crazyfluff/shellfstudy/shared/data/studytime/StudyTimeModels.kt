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
    /** The whole WaniKani history priced at the learner's pace; null before there is any. */
    val lifetime: LifetimeEstimate? = null
)
