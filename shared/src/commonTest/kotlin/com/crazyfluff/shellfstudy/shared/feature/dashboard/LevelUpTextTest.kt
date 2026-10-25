package com.crazyfluff.shellfstudy.shared.feature.dashboard

import com.crazyfluff.shellfstudy.shared.data.model.LevelItem
import com.crazyfluff.shellfstudy.shared.data.model.LevelUpStep
import com.crazyfluff.shellfstudy.shared.data.model.SrsStage
import com.crazyfluff.shellfstudy.shared.network.SubjectType
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class LevelUpTextTest {

    private val utc = TimeZone.UTC

    /** A Sunday. */
    private val now = Instant.parse("2026-10-04T10:37:00Z")

    @Test
    fun relativeEtaRoundsDownToWholeHoursAndNeverGoesNegative() {
        assertEquals("in under 1h", relativeEta(now + 59.minutes, now, utc))
        assertEquals("in 5h", relativeEta(now + 5.hours + 59.minutes, now, utc))
        assertEquals("in 1d", relativeEta(now + 1.days, now, utc))
        assertEquals("in 2d 6h", relativeEta(now + 2.days + 6.hours, now, utc))
        assertEquals("in under 1h", relativeEta(now - 3.hours, now, utc))
    }

    @Test
    fun etaCaptionSaysTodayTomorrowOrTheWeekday() {
        assertEquals(
            "Fastest level-up · Today 15:00 (in 4h)",
            levelUpEtaCaption(Instant.parse("2026-10-04T15:00:00Z"), now, is24h = true, zone = utc)
        )
        assertEquals(
            "Fastest level-up · Tomorrow 3:00 AM (in 16h)",
            levelUpEtaCaption(Instant.parse("2026-10-05T03:00:00Z"), now, is24h = false, zone = utc)
        )
        assertEquals(
            "Fastest level-up · Wed 20:00 (in 3d 9h)",
            levelUpEtaCaption(Instant.parse("2026-10-07T20:00:00Z"), now, is24h = true, zone = utc)
        )
    }

    @Test
    fun durationReadsInTheTwoLargestUnitsThatFitItsSize() {
        fun span(to: String) = durationText(Instant.parse("2026-10-04T10:00:00Z"), Instant.parse(to), utc)

        assertEquals("13d 23h", span("2026-10-18T09:00:00Z"))
        assertEquals("2w", span("2026-10-18T10:00:00Z"))
        assertEquals("8w 2d", span("2026-12-01T10:00:00Z"))
        // An hour short of two calendar months.
        assertEquals("8w 4d", span("2026-12-04T09:00:00Z"))
        assertEquals("2mo", span("2026-12-04T10:00:00Z"))
        assertEquals("11mo 29d", span("2027-10-03T10:00:00Z"))
        assertEquals("1y", span("2027-10-04T10:00:00Z"))
        assertEquals("1y 4mo", span("2028-02-20T10:00:00Z"))
        // Level 5 to level 60 at about a week a level.
        assertEquals("7y 6mo", span("2034-04-10T10:00:00Z"))
    }

    @Test
    fun etaCaptionGivesTheMonthAndDayPastTheComingWeekAndTheYearOnceItChanges() {
        assertEquals(
            "Fastest level-up · Sat 20:00 (in 6d 9h)",
            levelUpEtaCaption(Instant.parse("2026-10-10T20:00:00Z"), now, is24h = true, zone = utc)
        )
        assertEquals(
            "Fastest level-up · Oct 11 20:00 (in 7d 9h)",
            levelUpEtaCaption(Instant.parse("2026-10-11T20:00:00Z"), now, is24h = true, zone = utc)
        )
        assertEquals(
            "Unlocks · Mar 14, 2028 9:00 AM (in 1y 5mo)",
            unlockEtaCaption(Instant.parse("2028-03-14T09:00:00Z"), now, is24h = false, zone = utc)
        )
    }

    @Test
    fun levelDurationCaptionSaysHowLongTheLevelTakes() {
        assertEquals(
            "Level takes 6d 21h",
            levelDurationCaption(
                Instant.parse("2027-03-14T09:00:00Z"),
                Instant.parse("2027-03-21T06:00:00Z"),
                utc
            )
        )
    }

    @Test
    fun kanjiLeftCaptionSaysHowManyKanjiTheLevelUpNeeds() {
        assertEquals("12 kanji left to Guru", kanjiLeftCaption(12))
    }

    @Test
    fun lessonsCaptionCountsOnlyLessons() {
        assertEquals(
            "Lessons ready now: 2 kanji · 1 radical",
            lessonsCaption(LevelUpStep(now, kanjiLessons = 2, radicalLessons = 1, kanjiReviews = 3, kanjiLeft = 9))
        )
        assertEquals(
            "Lessons ready now: 3 radicals",
            lessonsCaption(LevelUpStep(now, radicalLessons = 3, kanjiLeft = 9))
        )
        assertNull(lessonsCaption(LevelUpStep(now, kanjiReviews = 3, kanjiLeft = 9)))
    }

    @Test
    fun reviewsCaptionSaysNowOrWhenTheyComeDue() {
        fun caption(step: LevelUpStep) = reviewsCaption(step, now, is24h = true, zone = utc)
        val later = Instant.parse("2026-10-04T14:00:00Z")

        assertEquals(
            "Next reviews: Today 14:00 · 4 radicals",
            caption(LevelUpStep(later, radicalReviews = 4, kanjiLeft = 12))
        )
        assertEquals(
            "Reviews ready now: 3 kanji · 1 radical",
            caption(LevelUpStep(now - 2.hours, kanjiReviews = 3, radicalReviews = 1, kanjiLessons = 2, kanjiLeft = 9))
        )
        assertNull(caption(LevelUpStep(now, kanjiLessons = 1, kanjiLeft = 1)))
    }

    private fun item(id: Long, passed: Boolean) = LevelItem(
        subjectId = id,
        subjectType = SubjectType.KANJI,
        characters = "$id",
        display = "$id",
        passed = passed,
        srsStage = if (passed) SrsStage.GURU_1 else SrsStage.APPRENTICE_1
    )

    @Test
    fun sortingPutsPassedFirstThenGuruTimeThenUnknown() {
        val guruTimes = mapOf(1L to now + 4.hours, 2L to now + 30.hours)
        val items = listOf(
            item(99, passed = false),
            item(2, passed = false),
            item(7, passed = true),
            item(1, passed = false),
            item(8, passed = true)
        )

        assertEquals(listOf(7L, 8L, 1L, 2L, 99L), sortedByLevelUp(items, guruTimes).map { it.subjectId })
    }

    @Test
    fun sortingLeavesRowsWithoutLevelUpInfoAlone() {
        val items = listOf(item(5, passed = false), item(3, passed = true))

        assertEquals(items, sortedByLevelUp(items, mapOf(1L to now + 4.hours)))
    }
}
