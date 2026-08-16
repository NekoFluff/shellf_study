package com.crazyfluff.shellfstudy.shared.feature.dashboard

import com.crazyfluff.shellfstudy.shared.data.model.ActivityBuckets
import com.crazyfluff.shellfstudy.shared.data.model.ActivityStats
import com.crazyfluff.shellfstudy.shared.data.model.FriendStats
import com.crazyfluff.shellfstudy.shared.data.model.LeaderboardMetric
import com.crazyfluff.shellfstudy.shared.data.model.LeaderboardWindow
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Instant

/**
 * Tests for buildActivityBars and subtitle functions. All nowMillis values are fixed at
 * 2026-08-15T12:00:00Z (Saturday, Aug 2026) with TimeZone.UTC so results are unambiguous.
 */
class ActivityBarBuilderTest {

    private val tz = TimeZone.UTC
    // 2026-08-15 noon UTC — Aug 2026, nowTotalMonths = 2026*12+7 = 24319
    private val nowMillis = Instant.parse("2026-08-15T12:00:00Z").toEpochMilliseconds()

    private fun entry(
        learnedBuckets: ActivityBuckets = ActivityBuckets(),
        burnedBuckets: ActivityBuckets = ActivityBuckets()
    ) = FriendStats(
        friendEntryId = "",
        nickname = "Test",
        username = "",
        level = 1,
        reviewAccuracy = 1f,
        avgDaysPerLevel = null,
        daysSinceStart = null,
        levelTimeline = emptyList(),
        isCurrentUser = false,
        learned = ActivityStats(),
        burned = ActivityStats(),
        learnedBuckets = learnedBuckets,
        burnedBuckets = burnedBuckets
    )

    // -------------------------------------------------------------------------
    // WEEK
    // -------------------------------------------------------------------------

    @Test
    fun week_produces7Bars() {
        val bars = buildActivityBars(listOf(entry()), LeaderboardMetric.LEARNED, LeaderboardWindow.WEEK, nowMillis, tz)
        assertEquals(7, bars.size)
    }

    @Test
    fun week_countsMatchWeekDays() {
        val weekDays = listOf(1, 2, 3, 4, 5, 6, 7)
        val e = entry(learnedBuckets = ActivityBuckets(weekDays = weekDays))
        val bars = buildActivityBars(listOf(e), LeaderboardMetric.LEARNED, LeaderboardWindow.WEEK, nowMillis, tz)
        assertEquals(weekDays, bars.map { it.counts.first() })
    }

    @Test
    fun week_labelsAreFormattedDates() {
        // nowMillis = 2026-08-15; 6 days ago = 2026-08-09
        val bars = buildActivityBars(listOf(entry()), LeaderboardMetric.LEARNED, LeaderboardWindow.WEEK, nowMillis, tz)
        assertEquals("8/9", bars[0].label)
        assertEquals("8/15", bars[6].label)
    }

    // -------------------------------------------------------------------------
    // MONTH
    // -------------------------------------------------------------------------

    @Test
    fun month_produces4Bars() {
        val bars = buildActivityBars(listOf(entry()), LeaderboardMetric.LEARNED, LeaderboardWindow.MONTH, nowMillis, tz)
        assertEquals(4, bars.size)
    }

    @Test
    fun month_labelsAreWeekLabels() {
        val bars = buildActivityBars(listOf(entry()), LeaderboardMetric.LEARNED, LeaderboardWindow.MONTH, nowMillis, tz)
        assertEquals(listOf("4w ago", "3w ago", "2w ago", "This wk"), bars.map { it.label })
    }

    @Test
    fun month_countsAreGroupSums() {
        // monthDays: 7×1 + 7×2 + 7×3 + 9×4
        val monthDays = List(7) { 1 } + List(7) { 2 } + List(7) { 3 } + List(9) { 4 }
        val e = entry(learnedBuckets = ActivityBuckets(monthDays = monthDays))
        val bars = buildActivityBars(listOf(e), LeaderboardMetric.LEARNED, LeaderboardWindow.MONTH, nowMillis, tz)
        assertEquals(listOf(7, 14, 21, 36), bars.map { it.counts.first() })
    }

    // -------------------------------------------------------------------------
    // YEAR
    // -------------------------------------------------------------------------

    @Test
    fun year_produces12Bars() {
        val bars = buildActivityBars(listOf(entry()), LeaderboardMetric.LEARNED, LeaderboardWindow.YEAR, nowMillis, tz)
        assertEquals(12, bars.size)
    }

    @Test
    fun year_firstBarIsTwelveMonthsAgo() {
        // 12 months before Aug 2026 = Sep 2025
        val bars = buildActivityBars(listOf(entry()), LeaderboardMetric.LEARNED, LeaderboardWindow.YEAR, nowMillis, tz)
        assertEquals("Sep", bars.first().label)
    }

    @Test
    fun year_lastBarIsCurrentMonth() {
        val bars = buildActivityBars(listOf(entry()), LeaderboardMetric.LEARNED, LeaderboardWindow.YEAR, nowMillis, tz)
        assertEquals("Aug", bars.last().label)
    }

    @Test
    fun year_countsMatchYearMonths() {
        val yearMonths = (1..12).toList()
        val e = entry(learnedBuckets = ActivityBuckets(yearMonths = yearMonths))
        val bars = buildActivityBars(listOf(e), LeaderboardMetric.LEARNED, LeaderboardWindow.YEAR, nowMillis, tz)
        assertEquals(yearMonths, bars.map { it.counts.first() })
    }

    // -------------------------------------------------------------------------
    // ALL_TIME — populated allTimeMonths
    // -------------------------------------------------------------------------

    @Test
    fun allTime_populatedData_barsSpanFullHistory() {
        // 3-month history spans Jun-Jul-Aug 2026
        val allTimeMonths = listOf(5, 3, 7)
        val e = entry(learnedBuckets = ActivityBuckets(allTimeMonths = allTimeMonths))
        val bars = buildActivityBars(listOf(e), LeaderboardMetric.LEARNED, LeaderboardWindow.ALL_TIME, nowMillis, tz)
        assertEquals(3, bars.size)
        assertEquals(listOf(5, 3, 7), bars.map { it.counts.first() })
    }

    @Test
    fun allTime_populatedData_labelsHaveYearSuffix() {
        // nowTotalMonths = 24319; 3 months → earliest = 24317 = Jun 2026
        val allTimeMonths = listOf(1, 2, 3)
        val e = entry(learnedBuckets = ActivityBuckets(allTimeMonths = allTimeMonths))
        val bars = buildActivityBars(listOf(e), LeaderboardMetric.LEARNED, LeaderboardWindow.ALL_TIME, nowMillis, tz)
        assertEquals("Jun '26", bars[0].label)
        assertEquals("Jul '26", bars[1].label)
        assertEquals("Aug '26", bars[2].label)
    }

    // -------------------------------------------------------------------------
    // ALL_TIME — old cache fallback (empty allTimeMonths)
    // -------------------------------------------------------------------------

    @Test
    fun allTime_emptyAllTimeMonths_fallsBackTo12Months() {
        val yearMonths = (1..12).toList()
        val e = entry(learnedBuckets = ActivityBuckets(yearMonths = yearMonths, allTimeMonths = emptyList()))
        val bars = buildActivityBars(listOf(e), LeaderboardMetric.LEARNED, LeaderboardWindow.ALL_TIME, nowMillis, tz)
        assertEquals(12, bars.size)
        assertEquals(yearMonths, bars.map { it.counts.first() })
    }

    // -------------------------------------------------------------------------
    // ALL_TIME — multi-user alignment
    // -------------------------------------------------------------------------

    @Test
    fun allTime_shorterHistoryLeftPaddedWithZeros() {
        val longHistory = listOf(1, 2, 3, 4, 5)
        val shortHistory = listOf(10, 20)  // aligned to right edge
        val e1 = entry(learnedBuckets = ActivityBuckets(allTimeMonths = longHistory))
        val e2 = entry(learnedBuckets = ActivityBuckets(allTimeMonths = shortHistory))
        val bars = buildActivityBars(listOf(e1, e2), LeaderboardMetric.LEARNED, LeaderboardWindow.ALL_TIME, nowMillis, tz)
        assertEquals(5, bars.size)
        assertEquals(listOf(1, 0), bars[0].counts)
        assertEquals(listOf(4, 10), bars[3].counts)
        assertEquals(listOf(5, 20), bars[4].counts)
    }

    // -------------------------------------------------------------------------
    // Metric routing
    // -------------------------------------------------------------------------

    @Test
    fun learned_usesLearnedBuckets() {
        val e = entry(
            learnedBuckets = ActivityBuckets(weekDays = List(7) { 10 }),
            burnedBuckets = ActivityBuckets(weekDays = List(7) { 99 })
        )
        val bars = buildActivityBars(listOf(e), LeaderboardMetric.LEARNED, LeaderboardWindow.WEEK, nowMillis, tz)
        assertEquals(10, bars[0].counts.first())
    }

    @Test
    fun burned_usesBurnedBuckets() {
        val e = entry(
            learnedBuckets = ActivityBuckets(weekDays = List(7) { 10 }),
            burnedBuckets = ActivityBuckets(weekDays = List(7) { 99 })
        )
        val bars = buildActivityBars(listOf(e), LeaderboardMetric.BURNED, LeaderboardWindow.WEEK, nowMillis, tz)
        assertEquals(99, bars[0].counts.first())
    }

    // -------------------------------------------------------------------------
    // Subtitle functions
    // -------------------------------------------------------------------------

    @Test
    fun activitySubtitle_allTimeDistinctFromYear() {
        assertNotEquals(activityChartSubtitle(LeaderboardWindow.YEAR), activityChartSubtitle(LeaderboardWindow.ALL_TIME))
    }

    @Test
    fun activitySubtitle_allTime() {
        assertEquals("Cumulative — all time", activityChartSubtitle(LeaderboardWindow.ALL_TIME))
    }

    @Test
    fun levelSubtitle_allTimeDistinctFromYear() {
        assertNotEquals(levelChartSubtitle(LeaderboardWindow.YEAR), levelChartSubtitle(LeaderboardWindow.ALL_TIME))
    }

    @Test
    fun levelSubtitle_allTime() {
        assertEquals("Full progression", levelChartSubtitle(LeaderboardWindow.ALL_TIME))
    }
}
