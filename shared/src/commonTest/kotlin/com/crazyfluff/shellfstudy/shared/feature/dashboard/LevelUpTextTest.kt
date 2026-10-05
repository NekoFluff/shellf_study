package com.crazyfluff.shellfstudy.shared.feature.dashboard

import com.crazyfluff.shellfstudy.shared.data.model.LevelItem
import com.crazyfluff.shellfstudy.shared.data.model.LevelUpStep
import com.crazyfluff.shellfstudy.shared.data.model.SrsStage
import com.crazyfluff.shellfstudy.shared.network.SubjectType
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
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
        assertEquals("in under 1h", relativeEta(now + 59.minutes, now))
        assertEquals("in 5h", relativeEta(now + 5.hours + 59.minutes, now))
        assertEquals("in 1d", relativeEta(now + 1.days, now))
        assertEquals("in 2d 6h", relativeEta(now + 2.days + 6.hours, now))
        assertEquals("in under 1h", relativeEta(now - 3.hours, now))
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
    fun nextStepCaptionNamesTheItemsAndKind() {
        fun caption(step: LevelUpStep) = nextStepCaption(step, now, is24h = true, zone = utc)
        val later = Instant.parse("2026-10-04T14:00:00Z")

        assertEquals("Next: Today 14:00 · 4 radical reviews", caption(LevelUpStep(later, 4, 0, 0)))
        assertEquals("Next: now · 1 kanji lesson", caption(LevelUpStep(now, 0, 1, 1)))
        assertEquals("Next: now · 3 kanji reviews", caption(LevelUpStep(now, 0, 3, 0)))
        // Mixed subject types, or lessons with reviews, list the counts without a kind.
        assertEquals("Next: now · 2 radicals, 3 kanji", caption(LevelUpStep(now, 2, 3, 0)))
        assertEquals("Next: now · 1 radical, 1 kanji", caption(LevelUpStep(now, 1, 1, 1)))
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
