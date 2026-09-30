package com.crazyfluff.shellfstudy.shared.data.studytime

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.FixedOffsetTimeZone
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class StudyTimeAggregatorTest {
    private val utc = TimeZone.UTC

    /** A Thursday. */
    private val today = LocalDate.parse("2026-09-24")

    private fun segment(
        startIso: String,
        minutes: Long,
        kind: StudyKind = StudyKind.REVIEW,
        level: Int? = null,
        items: Int = 0
    ) = StudySegment(kind, Instant.parse(startIso).toEpochMilliseconds(), minutes * MINUTE, level, items)

    private fun split(lessonMinutes: Long = 0, reviewMinutes: Long = 0) =
        StudyTimeSplit(lessonMs = lessonMinutes * MINUTE, reviewMs = reviewMinutes * MINUTE)

    // --- slicing ---

    @Test
    fun slice_cutsASegmentAtTheLocalHourBoundary() {
        val slices = StudyTimeAggregator.slice(listOf(segment("2026-09-24T10:40:00Z", 40)), utc)

        assertEquals(listOf(10 to 20 * MINUTE, 11 to 20 * MINUTE), slices.map { it.hour to it.ms })
    }

    @Test
    fun slice_usesTheZonesOwnHourBoundariesInAHalfHourZone() {
        val india = FixedOffsetTimeZone(UtcOffset(hours = 5, minutes = 30))
        // 04:20Z is 09:50 local, so the cut falls at 10:00 local (04:30Z), not at 05:00Z.
        val slices = StudyTimeAggregator.slice(listOf(segment("2026-09-24T04:20:00Z", 20)), india)

        assertEquals(listOf(9 to 10 * MINUTE, 10 to 10 * MINUTE), slices.map { it.hour to it.ms })
    }

    @Test
    fun dailyTotals_splitASessionRunningPastMidnightAcrossBothDays() {
        val daily = StudyTimeAggregator.dailyTotals(
            StudyTimeAggregator.slice(listOf(segment("2026-09-23T23:50:00Z", 20)), utc)
        )

        assertEquals(split(reviewMinutes = 10), daily[LocalDate.parse("2026-09-23")])
        assertEquals(split(reviewMinutes = 10), daily[today])
    }

    @Test
    fun dailyTotals_keepLessonsAndReviewsApart() {
        val daily = StudyTimeAggregator.dailyTotals(
            StudyTimeAggregator.slice(
                listOf(
                    segment("2026-09-24T08:00:00Z", 15, StudyKind.REVIEW),
                    segment("2026-09-24T09:00:00Z", 25, StudyKind.LESSON)
                ),
                utc
            )
        )

        assertEquals(split(lessonMinutes = 25, reviewMinutes = 15), daily[today])
    }

    // --- chart buckets ---

    @Test
    fun weekBuckets_areSevenZeroFilledDaysEndingToday() {
        val daily = mapOf(
            today to split(reviewMinutes = 30),
            LocalDate.parse("2026-09-20") to split(lessonMinutes = 10)
        )

        val buckets = StudyTimeAggregator.buckets(daily, today, StudyTimeWindow.WEEK)

        assertEquals(7, buckets.size)
        assertEquals(LocalDate.parse("2026-09-18"), buckets.first().start)
        assertEquals(today, buckets.last().start)
        assertEquals(split(lessonMinutes = 10), buckets[2].split)
        assertEquals(StudyTimeSplit.ZERO, buckets[1].split)
        assertEquals(split(reviewMinutes = 30), buckets.last().split)
    }

    @Test
    fun yearBuckets_areFiftyTwoWeeksWithTheLastEndingToday() {
        val daily = mapOf(
            today to split(reviewMinutes = 5),
            LocalDate.parse("2026-09-18") to split(reviewMinutes = 7),
            // One day before the last bucket starts, so it belongs to the one before.
            LocalDate.parse("2026-09-17") to split(reviewMinutes = 100)
        )

        val buckets = StudyTimeAggregator.buckets(daily, today, StudyTimeWindow.YEAR)

        assertEquals(52, buckets.size)
        assertEquals(LocalDate.parse("2026-09-18"), buckets.last().start)
        assertEquals(split(reviewMinutes = 12), buckets.last().split)
        assertEquals(split(reviewMinutes = 100), buckets[50].split)
    }

    // --- period stats ---

    @Test
    fun periodStats_averageOverEveryDayOfTheWindowNotJustActiveOnes() {
        val daily = mapOf(
            today to split(reviewMinutes = 40),
            LocalDate.parse("2026-09-22") to split(lessonMinutes = 30),
            // In the previous window.
            LocalDate.parse("2026-09-15") to split(reviewMinutes = 35)
        )

        val stats = StudyTimeAggregator.periodStats(daily, emptyList(), today, utc, StudyTimeWindow.WEEK)

        assertEquals(70 * MINUTE, stats.total.totalMs)
        assertEquals(10 * MINUTE, stats.dailyAverageMs)
        assertEquals(35 * MINUTE, stats.previousTotalMs)
        assertEquals(1.0, stats.changeFraction)
    }

    @Test
    fun periodStats_haveNoChangeOrItemsWithoutData() {
        val stats = StudyTimeAggregator.periodStats(emptyMap(), emptyList(), today, utc, StudyTimeWindow.MONTH)

        assertNull(stats.changeFraction)
        assertEquals(0, stats.reviewItems)
        assertEquals(0, stats.lessonItems)
    }

    @Test
    fun periodStats_countItemsFinishedInTheWindowByKind() {
        val segments = listOf(
            segment("2026-09-24T08:00:00Z", 10, StudyKind.REVIEW, items = 60),
            segment("2026-09-19T08:00:00Z", 10, StudyKind.REVIEW, items = 40),
            segment("2026-09-22T08:00:00Z", 20, StudyKind.LESSON, items = 5),
            // Before the 7-day window.
            segment("2026-09-17T08:00:00Z", 10, StudyKind.REVIEW, items = 99)
        )

        val stats = StudyTimeAggregator.periodStats(emptyMap(), segments, today, utc, StudyTimeWindow.WEEK)

        assertEquals(100, stats.reviewItems)
        assertEquals(5, stats.lessonItems)
    }

    // --- pace ---

    @Test
    fun pace_isTimePerCompletedItemForEachSpan() {
        val segments = listOf(
            segment("2026-09-24T08:00:00Z", 10, StudyKind.REVIEW, items = 60), // 10s per item
            segment("2026-09-20T08:00:00Z", 10, StudyKind.REVIEW, items = 40), // together: 12s per item
            segment("2026-09-05T08:00:00Z", 10, StudyKind.REVIEW, items = 40), // previous span: 15s
            segment("2026-09-24T09:00:00Z", 20, StudyKind.LESSON, items = 10) // 2m per lesson
        )

        val pace = StudyTimeAggregator.pace(segments, today, utc)

        assertEquals(12_000L, pace.reviewMsPerItem)
        assertEquals(15_000L, pace.previousReviewMsPerItem)
        assertEquals(120_000L, pace.lessonMsPerItem)
        assertNull(pace.previousLessonMsPerItem)
    }

    @Test
    fun pace_countsStudyPhaseTimeWithNoItemsTowardLessons() {
        // Reading the cards records a stretch with no items; the quiz that follows records the items.
        val segments = listOf(
            segment("2026-09-24T08:00:00Z", 6, StudyKind.LESSON, items = 0),
            segment("2026-09-24T08:10:00Z", 4, StudyKind.LESSON, items = 5)
        )

        assertEquals(120_000L, StudyTimeAggregator.pace(segments, today, utc).lessonMsPerItem)
    }

    @Test
    fun pace_isNullWithoutItems() {
        val pace = StudyTimeAggregator.pace(listOf(segment("2026-09-24T08:00:00Z", 5)), today, utc)

        assertNull(pace.reviewMsPerItem)
    }

    @Test
    fun estimateQueue_usesThePaceAndFallsBackToDefaults() {
        val pace = StudyPace(reviewMsPerItem = 9_000L, lessonMsPerItem = 90_000L)
        val ownPace = StudyTimeAggregator.estimateQueue(100, 5, pace)
        assertEquals(900_000L, ownPace.reviewMs)
        assertEquals(450_000L, ownPace.lessonMs)

        val noPace = StudyTimeAggregator.estimateQueue(10, 1, StudyPace())
        assertEquals(10 * StudyTimeAggregator.DEFAULT_REVIEW_MS_PER_ITEM, noPace.reviewMs)
        assertEquals(StudyTimeAggregator.DEFAULT_LESSON_MS_PER_ITEM, noPace.lessonMs)
    }

    // --- levels ---

    @Test
    fun levelTotals_areHighestFirstAndSkipUnknownLevels() {
        val levels = StudyTimeAggregator.levelTotals(
            StudyTimeAggregator.slice(
                listOf(
                    segment("2026-09-20T08:00:00Z", 30, StudyKind.REVIEW, level = 4),
                    segment("2026-09-23T08:00:00Z", 20, StudyKind.LESSON, level = 5),
                    segment("2026-09-24T08:00:00Z", 10, StudyKind.REVIEW, level = 5),
                    segment("2026-09-24T09:00:00Z", 99, StudyKind.REVIEW, level = null)
                ),
                utc
            )
        )

        assertEquals(listOf(5, 4), levels.map { it.level })
        assertEquals(split(lessonMinutes = 20, reviewMinutes = 10), levels[0].split)
        assertEquals(2, levels[0].activeDays)
    }

    // --- heatmap ---

    @Test
    fun heatmap_placesTimeByWeekdayAndHourWithinTheLastNinetyDays() {
        val heatmap = StudyTimeAggregator.heatmap(
            StudyTimeAggregator.slice(
                listOf(
                    segment("2026-09-24T19:00:00Z", 30), // Thursday evening
                    segment("2026-01-01T19:00:00Z", 30) // too old
                ),
                utc
            ),
            today
        )

        assertEquals(30 * MINUTE, heatmap.at(DayOfWeek.THURSDAY, 19))
        assertEquals(30 * MINUTE, heatmap.cellsMs.sum())
    }

    @Test
    fun heatmap_namesThePeakPartOfDayAndWhetherWeekendsWin() {
        val heatmap = StudyTimeAggregator.heatmap(
            StudyTimeAggregator.slice(
                listOf(
                    segment("2026-09-19T07:00:00Z", 60), // Saturday morning
                    segment("2026-09-24T19:00:00Z", 20) // Thursday evening
                ),
                utc
            ),
            today
        )

        assertEquals(PartOfDay.MORNING, heatmap.peakPartOfDay)
        assertEquals(true, heatmap.prefersWeekends)
        assertNull(StudyHeatmap.EMPTY.peakPartOfDay)
    }

    // --- goal streak ---

    @Test
    fun goalStreak_countsTodayOnlyOnceMetAndIsNotBrokenByAnUnfinishedToday() {
        val goal = 30 * MINUTE
        val daily = mapOf(
            LocalDate.parse("2026-09-21") to split(reviewMinutes = 45),
            LocalDate.parse("2026-09-22") to split(reviewMinutes = 30),
            LocalDate.parse("2026-09-23") to split(lessonMinutes = 20, reviewMinutes = 15),
            today to split(reviewMinutes = 5)
        )

        assertEquals(3, StudyTimeAggregator.goalStreak(daily, today, goal))
        assertEquals(4, StudyTimeAggregator.goalStreak(daily + (today to split(reviewMinutes = 30)), today, goal))
    }

    @Test
    fun goalStreak_breaksOnAMissedDay() {
        val daily = mapOf(
            LocalDate.parse("2026-09-21") to split(reviewMinutes = 45),
            LocalDate.parse("2026-09-23") to split(reviewMinutes = 45)
        )

        assertEquals(1, StudyTimeAggregator.goalStreak(daily, today, 30 * MINUTE))
    }

    // --- report ---

    @Test
    fun report_withNoSegmentsHasNoDataButStillAFullChart() {
        val report = StudyTimeAggregator.report(
            emptyList(), today, utc, StudyTimeWindow.MONTH, 30 * MINUTE, currentLevel = 7
        )

        assertFalse(report.overview.hasAnyData)
        assertEquals(30, report.buckets.size)
        assertTrue(report.heatmap.isEmpty)
        assertTrue(report.levels.isEmpty())
        assertEquals(0f, report.overview.goalFraction)
    }

    @Test
    fun overview_capsGoalProgressAtFull() {
        val overview = StudyTimeAggregator.overview(
            listOf(segment("2026-09-24T08:00:00Z", 45)), today, utc, 30 * MINUTE
        )

        assertEquals(45 * MINUTE, overview.today.totalMs)
        assertEquals(1f, overview.goalFraction)
        assertEquals(1, overview.goalStreakDays)
    }

    @Test
    fun overview_totalsRecordedTimePerLevelAndSkipsUnknownLevels() {
        val overview = StudyTimeAggregator.overview(
            listOf(
                segment("2026-09-20T08:00:00Z", 30, StudyKind.REVIEW, level = 7),
                segment("2026-09-23T08:00:00Z", 20, StudyKind.LESSON, level = 8),
                segment("2026-09-24T08:00:00Z", 10, StudyKind.REVIEW, level = 8),
                segment("2026-09-24T09:00:00Z", 99, StudyKind.REVIEW, level = null)
            ),
            today, utc, 30 * MINUTE
        )

        assertEquals(mapOf(7 to 30 * MINUTE, 8 to 30 * MINUTE), overview.levelTotalsMs)
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
